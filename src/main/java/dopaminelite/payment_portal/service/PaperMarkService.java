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
import dopaminelite.payment_portal.entity.MarkOwner;
import dopaminelite.payment_portal.entity.MarkStudentSnapshot;
import dopaminelite.payment_portal.entity.PaperMark;
import dopaminelite.payment_portal.exception.DuplicateResourceException;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.PaperMarkMapper;
import dopaminelite.payment_portal.repository.PaperMarkRepository;
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
    private final PaperMarkMapper paperMarkMapper;
    private final StudentLookupService studentLookupService;
    /**
     * Every method below is addressed by paper-event id but operates on whatever actually owns
     * that paper-event's grading — itself, or the correlation grouping it with the other sitting
     * of the same real paper. See {@link MarkOwnerResolver}.
     */
    private final MarkOwnerResolver markOwnerResolver;

    /**
     * Lists marks recorded for a paper, most recently entered first.
     *
     * @param paperId the paper's ID
     * @param limit maximum number of results per page
     * @param offset number of results to skip
     * @param onlyMineInstructorId when non-null, narrows the list to marks this instructor
     *        entered themselves — backs the marks page's "You" / "All" toggle
     * @param studentCodeNumber when non-blank, narrows the list to that one student's mark so an
     *        instructor can pull up a student directly instead of paging for them. Trimmed here
     *        rather than trusted: a code copied from a message or typed on a phone keyboard often
     *        carries a trailing space, which would otherwise match nothing at all.
     * @return paginated response containing the mark list and total count
     * @throws ResourceNotFoundException if no paper exists with the given ID
     */
    public PaginatedResponse<PaperMarkResponse> listMarks(UUID paperId, int limit, int offset,
                                                          UUID onlyMineInstructorId, String studentCodeNumber) {
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        String codeFilter = studentCodeNumber == null || studentCodeNumber.isBlank()
                ? null
                : studentCodeNumber.trim();

        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<PaperMark> page = paperMarkRepository.findByFilters(
                owner.getId(), onlyMineInstructorId, codeFilter, pageable);

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
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        StudentLookupDto student = studentLookupService.findByCodeNumber(studentCodeNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with code number: " + studentCodeNumber));

        boolean alreadyHasMark = paperMarkRepository.existsByOwnerIdAndStudentId(owner.getId(), student.getId());

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
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        if (owner.getMcqMaxMarks() == null && owner.getStructuredMaxMarks() == null && owner.getEssayMaxMarks() == null) {
            throw new ValidationException("Paper " + paperId + " has no mark scheme configured");
        }

        validateSectionOnCreate("MCQ", owner.getMcqMaxMarks(), request.getMcqMarks());
        validateSectionOnCreate("Structured", owner.getStructuredMaxMarks(), request.getStructuredMarks());
        validateSectionOnCreate("Essay", owner.getEssayMaxMarks(), request.getEssayMarks());
        validateBounds("Total marks", request.getTotalMarks(), ZERO, ONE_HUNDRED);

        StudentLookupDto student = studentLookupService.findByCodeNumber(request.getStudentCodeNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found with code number: " + request.getStudentCodeNumber()));

        if (paperMarkRepository.existsByOwnerIdAndStudentId(owner.getId(), student.getId())) {
            throw DuplicateResourceException.marksAlreadyExist(paperId, student.getId());
        }

        PaperMark mark = new PaperMark();
        mark.assignOwner(owner);
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
     * <p>Who is allowed to edit which mark is decided by the BFF, which owns authentication and
     * authorization: it reads the mark first and refuses an instructor editing one they didn't
     * enter. Deliberately not re-checked here — this service has no inbound authentication at all
     * (it doesn't even verify the token's signature), so a second copy of the rule here would add
     * no protection while giving two places for it to drift apart.
     *
     * @param paperId the paper's ID
     * @param markId the mark's ID
     * @param request the fields to update
     * @param instructorId the ID of the instructor making the edit, recorded as who last touched it
     * @return the updated mark
     * @throws ResourceNotFoundException if no mark exists with the given ID for that paper
     * @throws ValidationException if an updated value is out-of-bounds or for a disabled section
     */
    @Transactional
    public PaperMarkResponse updateMark(UUID paperId, UUID markId, PaperMarkUpdateRequest request,
                                         UUID instructorId) {
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        PaperMark mark = paperMarkRepository.findByIdAndOwnerId(markId, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paper mark not found with id: " + markId + " for paper: " + paperId));

        // Deliberately the resolved owner's scheme rather than mark.getPaper()'s: a
        // correlation-owned mark has no paper at all, and reading one off it would NPE.
        if (request.getMcqMarks() != null) {
            validateSectionOnUpdate("MCQ", owner.getMcqMaxMarks(), request.getMcqMarks());
            mark.setMcqMarks(request.getMcqMarks());
        }
        if (request.getStructuredMarks() != null) {
            validateSectionOnUpdate("Structured", owner.getStructuredMaxMarks(), request.getStructuredMarks());
            mark.setStructuredMarks(request.getStructuredMarks());
        }
        if (request.getEssayMarks() != null) {
            validateSectionOnUpdate("Essay", owner.getEssayMaxMarks(), request.getEssayMarks());
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
     * <p>As with {@link #updateMark}, whether this caller may delete this particular mark is the
     * BFF's decision, made before the request reaches here.
     *
     * @param paperId the paper's ID
     * @param markId the mark's ID
     * @throws ResourceNotFoundException if no mark exists with the given ID for that paper
     */
    @Transactional
    public void deleteMark(UUID paperId, UUID markId) {
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        PaperMark mark = paperMarkRepository.findByIdAndOwnerId(markId, owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paper mark not found with id: " + markId + " for paper: " + paperId));

        paperMarkRepository.delete(mark);
    }

    /**
     * A single mark, scoped to the paper it should belong to. Exists so the BFF can read who
     * entered a mark before deciding whether the caller is allowed to change it.
     *
     * @param paperId the paper's ID
     * @param markId the mark's ID
     * @return the mark
     * @throws ResourceNotFoundException if no mark exists with the given ID for that paper
     */
    public PaperMarkResponse getMark(UUID paperId, UUID markId) {
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        return paperMarkRepository.findByIdAndOwnerId(markId, owner.getId())
                .map(paperMarkMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paper mark not found with id: " + markId + " for paper: " + paperId));
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
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        if (!owner.isLeaderboardPublished() && !isPrivilegedForLeaderboard(callerRole)) {
            throw new ResourceNotFoundException("Leaderboard not published for paper: " + paperId);
        }

        return toLeaderboardResponse(paperId, owner, callerId, PageRequest.of(offset / limit, limit));
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
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        List<PaperMark> marks = paperMarkRepository.findByOwnerIdOrderByTotalMarksDesc(owner.getId());
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

        // The owner was loaded inside this transaction and so is managed either way (a Paper, or
        // a PaperCorrelation reached through it) - these writes flush on commit without needing
        // an explicit save against one of two different repositories.
        owner.setLeaderboardLastGeneratedAt(LocalDateTime.now());
        owner.setLeaderboardLastGeneratedBy(callerId);

        return toLeaderboardResponse(paperId, owner, null, DEFAULT_LEADERBOARD_PAGE);
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
        MarkOwner owner = markOwnerResolver.resolve(paperId);

        if (request.isPublished() && owner.getLeaderboardLastGeneratedAt() == null) {
            throw ValidationException.leaderboardNotGeneratedYet(paperId);
        }

        // Managed entity, flushed on commit - see generateRanks.
        owner.setLeaderboardPublished(request.isPublished());

        return toLeaderboardResponse(paperId, owner, null, DEFAULT_LEADERBOARD_PAGE);
    }

    private boolean isPrivilegedForLeaderboard(String callerRole) {
        return "INSTRUCTOR".equalsIgnoreCase(callerRole)
                || "ADMIN".equalsIgnoreCase(callerRole)
                || "MAIN_ADMIN".equalsIgnoreCase(callerRole);
    }

    /**
     * @param paperId the paper-event the caller addressed, echoed back unchanged so their existing
     *        links keep working — deliberately not the owner's id, which for a correlation is not
     *        a paper id at all
     * @param owner what actually holds the marks and leaderboard being described
     */
    private PaperLeaderboardResponse toLeaderboardResponse(UUID paperId, MarkOwner owner, UUID callerId, Pageable pageable) {
        Page<PaperMark> page = paperMarkRepository.findRankedByOwnerId(owner.getId(), pageable);
        List<LeaderboardEntryDto> entries = page.getContent().stream()
                .map(paperMarkMapper::toLeaderboardEntry)
                .toList();

        LeaderboardEntryDto callerEntry = callerId == null ? null : paperMarkRepository
                .findRankedByOwnerIdAndStudentId(owner.getId(), callerId)
                .map(paperMarkMapper::toLeaderboardEntry)
                .orElse(null);

        PaperLeaderboardResponse response = new PaperLeaderboardResponse();
        response.setPaperId(paperId);
        // For a correlation this is its display name, so the two sittings of one paper are never
        // shown to instructors or students under two different titles.
        response.setPaperTitle(owner.getDisplayTitle());
        response.setMarkScheme(new MarkSchemeDto(owner.getMcqMaxMarks(), owner.getStructuredMaxMarks(), owner.getEssayMaxMarks()));
        response.setPublished(owner.isLeaderboardPublished());
        response.setLastGeneratedAt(owner.getLeaderboardLastGeneratedAt());
        response.setLastGeneratedByInstructorId(owner.getLeaderboardLastGeneratedBy());
        response.setEntries(entries);
        response.setTotal(page.getTotalElements());
        response.setCallerEntry(callerEntry);
        return response;
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
