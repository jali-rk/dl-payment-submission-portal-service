package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.external.StudentLookupDto;
import dopaminelite.payment_portal.dto.paper.LeaderboardEntryDto;
import dopaminelite.payment_portal.dto.paper.LeaderboardVisibilityUpdateRequest;
import dopaminelite.payment_portal.dto.paper.MarkSchemeDto;
import dopaminelite.payment_portal.dto.paper.PaperLeaderboardResponse;
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
import java.time.LocalDateTime;
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

    /** Page used when building a leaderboard response for the generate/publish write endpoints,
     * which don't take their own pagination params — the caller re-fetches the leaderboard
     * separately if they need more than the first page. */
    private static final Pageable DEFAULT_LEADERBOARD_PAGE = PageRequest.of(0, 50);

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
     * Every mark recorded for a student, across all papers — backs the unified academic
     * profile's "all of this student's papers" view. Empty, not an error, for a student with no
     * marks yet.
     *
     * @param studentId the student's ID
     * @return the student's marks, most recent paper first
     */
    public List<PaperMarkResponse> listMarksForStudent(UUID studentId) {
        return paperMarkRepository.findByStudentId(studentId).stream()
                .map(paperMarkMapper::toResponse)
                .toList();
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

    /**
     * Retrieves a page of a paper's leaderboard: its publish state, when it was last generated,
     * a page of the ranked entries as of that generation, and the caller's own ranked entry
     * (independent of paging, so they never have to hunt for it across pages). A STUDENT (or
     * unrecognized/absent role) is only allowed to see it once published — instructors/admins/
     * main admins can always see it, hidden or not, mirroring {@code CalendarEventService}'s
     * existing role-based visibility pattern for papers/instructors.
     *
     * @param paperId the paper's ID
     * @param callerRole the caller's role, as forwarded by the BFF
     * @param callerId the caller's ID, or null if it couldn't be determined
     * @param limit maximum number of entries per page
     * @param offset number of entries to skip
     * @return the leaderboard page
     * @throws ResourceNotFoundException if no paper exists with the given ID, or if the caller
     *         isn't privileged and the leaderboard isn't published (treated as not existing,
     *         to avoid leaking who's ranked to an unauthorized viewer)
     */
    public PaperLeaderboardResponse getLeaderboard(UUID paperId, String callerRole, UUID callerId, int limit, int offset) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

        if (!paper.isLeaderboardPublished() && !isPrivilegedForLeaderboard(callerRole)) {
            throw new ResourceNotFoundException("Leaderboard not published for paper: " + paperId);
        }

        return toLeaderboardResponse(paper, callerId, PageRequest.of(offset / limit, limit));
    }

    /**
     * Recomputes leaderboard ranks for every mark currently recorded on a paper, using standard
     * competition ranking on {@link PaperMark#getTotalMarks()} (tied students share a rank; the
     * next distinct value skips accordingly). Marks added, edited, or deleted after this call
     * are not reflected on the leaderboard until it's generated again.
     *
     * @param paperId the paper's ID
     * @param callerId the ID of the instructor/admin/main admin generating ranks
     * @return the refreshed leaderboard
     * @throws ResourceNotFoundException if no paper exists with the given ID
     * @throws ValidationException if the paper has no marks recorded yet
     */
    @Transactional
    public PaperLeaderboardResponse generateRanks(UUID paperId, UUID callerId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

        List<PaperMark> marks = paperMarkRepository.findByPaperIdOrderByTotalMarksDesc(paperId);
        if (marks.isEmpty()) {
            throw ValidationException.noMarksToGenerateRanksFor(paperId);
        }

        BigDecimal previousMarks = null;
        int currentRank = 0;
        for (int position = 0; position < marks.size(); position++) {
            PaperMark mark = marks.get(position);
            if (previousMarks == null || mark.getTotalMarks().compareTo(previousMarks) != 0) {
                currentRank = position + 1;
                previousMarks = mark.getTotalMarks();
            }
            mark.setRank(currentRank);
        }
        paperMarkRepository.saveAll(marks);

        paper.setLeaderboardLastGeneratedAt(LocalDateTime.now());
        paper.setLeaderboardLastGeneratedBy(callerId);
        Paper savedPaper = paperRepository.save(paper);

        return toLeaderboardResponse(savedPaper, null, DEFAULT_LEADERBOARD_PAGE);
    }

    /**
     * Publishes or hides a paper's leaderboard. Publishing is rejected until ranks have been
     * generated at least once — there would otherwise be nothing meaningful to show.
     *
     * @param paperId the paper's ID
     * @param request the new visibility state
     * @return the updated leaderboard
     * @throws ResourceNotFoundException if no paper exists with the given ID
     * @throws ValidationException if attempting to publish before ranks have ever been generated
     */
    @Transactional
    public PaperLeaderboardResponse setLeaderboardVisibility(UUID paperId, LeaderboardVisibilityUpdateRequest request) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

        if (request.isPublished() && paper.getLeaderboardLastGeneratedAt() == null) {
            throw ValidationException.leaderboardNotGeneratedYet(paperId);
        }

        paper.setLeaderboardPublished(request.isPublished());
        Paper savedPaper = paperRepository.save(paper);

        return toLeaderboardResponse(savedPaper, null, DEFAULT_LEADERBOARD_PAGE);
    }

    private boolean isPrivilegedForLeaderboard(String callerRole) {
        return "INSTRUCTOR".equalsIgnoreCase(callerRole)
                || "ADMIN".equalsIgnoreCase(callerRole)
                || "MAIN_ADMIN".equalsIgnoreCase(callerRole);
    }

    private PaperLeaderboardResponse toLeaderboardResponse(Paper paper, UUID callerId, Pageable pageable) {
        Page<PaperMark> page = paperMarkRepository.findByPaperIdAndRankIsNotNullOrderByRankAscIdAsc(paper.getId(), pageable);
        List<LeaderboardEntryDto> entries = page.getContent().stream()
                .map(paperMarkMapper::toLeaderboardEntry)
                .toList();

        LeaderboardEntryDto callerEntry = callerId == null ? null : paperMarkRepository
                .findByPaperIdAndStudentIdAndRankIsNotNull(paper.getId(), callerId)
                .map(paperMarkMapper::toLeaderboardEntry)
                .orElse(null);

        PaperLeaderboardResponse response = new PaperLeaderboardResponse();
        response.setPaperId(paper.getId());
        response.setPaperTitle(paper.getTitle());
        response.setMarkScheme(new MarkSchemeDto(paper.getMcqMaxMarks(), paper.getStructuredMaxMarks(), paper.getEssayMaxMarks()));
        response.setPublished(paper.isLeaderboardPublished());
        response.setLastGeneratedAt(paper.getLeaderboardLastGeneratedAt());
        response.setLastGeneratedByInstructorId(paper.getLeaderboardLastGeneratedBy());
        response.setEntries(entries);
        response.setTotal(page.getTotalElements());
        response.setCallerEntry(callerEntry);
        return response;
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
