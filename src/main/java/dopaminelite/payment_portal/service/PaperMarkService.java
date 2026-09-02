package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.external.StudentLookupDto;
import dopaminelite.payment_portal.dto.paper.PaperMarkCreateRequest;
import dopaminelite.payment_portal.dto.paper.PaperMarkResponse;
import dopaminelite.payment_portal.dto.paper.PaperMarkUpdateRequest;
import dopaminelite.payment_portal.dto.paper.StudentVerificationResponse;
import dopaminelite.payment_portal.entity.MarkStudentSnapshot;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperMark;
import dopaminelite.payment_portal.exception.DuplicateResourceException;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.PaperMarkMapper;
import dopaminelite.payment_portal.repository.PaperMarkRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service for instructor-entered paper marks: verifying a student exists before recording
 * marks for them, and CRUD over the resulting {@link PaperMark} rows. Deliberately independent
 * of {@code PaymentSubmission}/{@code PaperSlot} — see {@link PaperMark}'s Javadoc.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaperMarkService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PaperMarkRepository paperMarkRepository;
    private final PaperRepository paperRepository;
    private final PaperMarkMapper paperMarkMapper;
    private final StudentLookupService studentLookupService;

    /**
     * Lists marks recorded for a paper, most recently entered first.
     *
     * @param paperId the paper's ID
     * @param limit maximum number of results per page
     * @param offset number of results to skip
     * @return paginated response containing the mark list and total count
     * @throws ResourceNotFoundException if no paper exists with the given ID
     */
    public PaginatedResponse<PaperMarkResponse> listMarks(UUID paperId, int limit, int offset) {
        requirePaperExists(paperId);

        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<PaperMark> page = paperMarkRepository.findByPaperId(paperId, pageable);

        List<PaperMarkResponse> items = page.getContent().stream()
                .map(paperMarkMapper::toResponse)
                .toList();

        return new PaginatedResponse<>(items, page.getTotalElements());
    }

    /**
     * Live lookup an instructor's UI can call as a student code number is typed, before
     * submitting the full marks form — confirms the student exists and whether they already
     * have a mark on this paper.
     *
     * @param paperId the paper's ID
     * @param studentCodeNumber the code number to verify
     * @return the resolved student's public data plus whether they already have a mark here
     * @throws ResourceNotFoundException if no paper exists with the given ID, or no student
     *         exists with that code number
     */
    public StudentVerificationResponse verifyStudent(UUID paperId, String studentCodeNumber) {
        requirePaperExists(paperId);

        StudentLookupDto student = studentLookupService.findByCodeNumber(studentCodeNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with code number: " + studentCodeNumber));

        boolean alreadyHasMark = paperMarkRepository.existsByPaperIdAndStudentId(paperId, student.getId());

        return new StudentVerificationResponse(
                student.getId(), student.getCodeNumber(), student.getFullName(),
                student.getEmail(), student.getWhatsappNumber(), alreadyHasMark);
    }

    /**
     * Records a new mark for a student on a paper.
     *
     * @param paperId the paper's ID
     * @param request the mark values and the student's code number
     * @param instructorId the ID of the instructor entering the mark
     * @return the created mark
     * @throws ResourceNotFoundException if no paper exists with the given ID, or no student
     *         exists with the given code number
     * @throws ValidationException if the paper has no mark scheme configured, or a section
     *         mark is missing/out-of-bounds/provided-for-a-disabled-section
     * @throws DuplicateResourceException if this student already has a mark on this paper
     */
    @Transactional
    public PaperMarkResponse createMark(UUID paperId, PaperMarkCreateRequest request, UUID instructorId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

        if (paper.getMcqMaxMarks() == null && paper.getStructuredMaxMarks() == null && paper.getEssayMaxMarks() == null) {
            throw new ValidationException("Paper " + paperId + " has no mark scheme configured");
        }

        validateSectionOnCreate("MCQ", paper.getMcqMaxMarks(), request.getMcqMarks());
        validateSectionOnCreate("Structured", paper.getStructuredMaxMarks(), request.getStructuredMarks());
        validateSectionOnCreate("Essay", paper.getEssayMaxMarks(), request.getEssayMarks());
        validateBounds("Total marks", request.getTotalMarks(), ZERO, ONE_HUNDRED);

        StudentLookupDto student = studentLookupService.findByCodeNumber(request.getStudentCodeNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with code number: " + request.getStudentCodeNumber()));

        if (paperMarkRepository.existsByPaperIdAndStudentId(paperId, student.getId())) {
            throw DuplicateResourceException.marksAlreadyExist(paperId, student.getId());
        }

        PaperMark mark = new PaperMark();
        mark.setPaper(paper);
        mark.setStudentId(student.getId());
        mark.setStudentSnapshot(toStudentSnapshot(student));
        mark.setMcqMarks(request.getMcqMarks());
        mark.setStructuredMarks(request.getStructuredMarks());
        mark.setEssayMarks(request.getEssayMarks());
        mark.setTotalMarks(request.getTotalMarks());
        mark.setEnteredByInstructorId(instructorId);
        mark.setLastUpdatedByInstructorId(instructorId);

        PaperMark saved = paperMarkRepository.save(mark);
        return paperMarkMapper.toResponse(saved);
    }

    /**
     * Partially updates an existing mark's values. The student a mark belongs to is never
     * editable here — only non-null fields in the request are applied.
     *
     * @param paperId the paper's ID
     * @param markId the mark's ID
     * @param request the fields to update
     * @param instructorId the ID of the instructor making the edit
     * @return the updated mark
     * @throws ResourceNotFoundException if no mark exists with the given ID for that paper
     * @throws ValidationException if an updated value is out-of-bounds or for a disabled section
     */
    @Transactional
    public PaperMarkResponse updateMark(UUID paperId, UUID markId, PaperMarkUpdateRequest request, UUID instructorId) {
        PaperMark mark = paperMarkRepository.findByIdAndPaperId(markId, paperId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paper mark not found with id: " + markId + " for paper: " + paperId));

        Paper paper = mark.getPaper();

        if (request.getMcqMarks() != null) {
            validateSectionOnUpdate("MCQ", paper.getMcqMaxMarks(), request.getMcqMarks());
            mark.setMcqMarks(request.getMcqMarks());
        }
        if (request.getStructuredMarks() != null) {
            validateSectionOnUpdate("Structured", paper.getStructuredMaxMarks(), request.getStructuredMarks());
            mark.setStructuredMarks(request.getStructuredMarks());
        }
        if (request.getEssayMarks() != null) {
            validateSectionOnUpdate("Essay", paper.getEssayMaxMarks(), request.getEssayMarks());
            mark.setEssayMarks(request.getEssayMarks());
        }
        if (request.getTotalMarks() != null) {
            validateBounds("Total marks", request.getTotalMarks(), ZERO, ONE_HUNDRED);
            mark.setTotalMarks(request.getTotalMarks());
        }
        mark.setLastUpdatedByInstructorId(instructorId);

        PaperMark saved = paperMarkRepository.save(mark);
        return paperMarkMapper.toResponse(saved);
    }

    /**
     * Deletes a mark. This is also the mechanism to bring a paper's mark count back to 0,
     * unlocking its mark scheme for editing again.
     *
     * @param paperId the paper's ID
     * @param markId the mark's ID
     * @throws ResourceNotFoundException if no mark exists with the given ID for that paper
     */
    @Transactional
    public void deleteMark(UUID paperId, UUID markId) {
        PaperMark mark = paperMarkRepository.findByIdAndPaperId(markId, paperId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paper mark not found with id: " + markId + " for paper: " + paperId));

        paperMarkRepository.delete(mark);
    }

    private void requirePaperExists(UUID paperId) {
        if (!paperRepository.existsById(paperId)) {
            throw new ResourceNotFoundException("Paper not found with id: " + paperId);
        }
    }

    private void validateSectionOnCreate(String sectionLabel, BigDecimal sectionMax, BigDecimal value) {
        if (sectionMax == null) {
            if (value != null) {
                throw new ValidationException("Paper does not have a " + sectionLabel + " section");
            }
            return;
        }
        if (value == null) {
            throw new ValidationException(sectionLabel + " marks are required for this paper");
        }
        validateBounds(sectionLabel + " marks", value, ZERO, sectionMax);
    }

    private void validateSectionOnUpdate(String sectionLabel, BigDecimal sectionMax, BigDecimal value) {
        if (sectionMax == null) {
            throw new ValidationException("Paper does not have a " + sectionLabel + " section");
        }
        validateBounds(sectionLabel + " marks", value, ZERO, sectionMax);
    }

    private void validateBounds(String label, BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new ValidationException(label + " must be between " + min + " and " + max + " (got " + value + ")");
        }
    }

    private MarkStudentSnapshot toStudentSnapshot(StudentLookupDto student) {
        MarkStudentSnapshot snapshot = new MarkStudentSnapshot();
        snapshot.setCodeNumber(student.getCodeNumber());
        snapshot.setFullName(student.getFullName());
        snapshot.setEmail(student.getEmail());
        snapshot.setWhatsappNumber(student.getWhatsappNumber());
        return snapshot;
    }

}
