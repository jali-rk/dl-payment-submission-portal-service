package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.paper.MarkSchemeDto;
import dopaminelite.payment_portal.dto.paper.PaperCreateRequest;
import dopaminelite.payment_portal.dto.paper.PaperResponse;
import dopaminelite.payment_portal.dto.paper.PaperUpdateRequest;
import dopaminelite.payment_portal.dto.paper.PaperWindowFilter;
import dopaminelite.payment_portal.entity.MarkOwner;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperCorrelation;
import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.StudentSnapshot;
import dopaminelite.payment_portal.entity.enums.PaperAuditAction;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.PaperMapper;
import dopaminelite.payment_portal.repository.PaperCorrelationRepository;
import dopaminelite.payment_portal.repository.PaperMarkRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
import dopaminelite.payment_portal.repository.PaperSlotRepository;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing paper business logic: creation, retrieval, updates, and validation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaperService {

    private final PaperRepository paperRepository;
    private final PaymentPortalRepository portalRepository;
    private final PaperMarkRepository paperMarkRepository;
    private final PaperCorrelationRepository correlationRepository;
    private final MarkOwnerResolver markOwnerResolver;
    private final PaperSlotRepository paperSlotRepository;
    private final PaperMapper paperMapper;
    private final CalendarEventService calendarEventService;
    private final PaperAuditService paperAuditService;

    /**
     * Retrieves a paginated list of papers with optional filtering.
     *
     * @param titleSearch case-insensitive substring match on title, null for no filtering
     * @param windowFilter filter by validity window (UPCOMING/ACTIVE/PAST), null for no filtering
     * @param limit maximum number of results per page
     * @param offset number of results to skip
     * @return paginated response containing paper list and total count
     */
    public PaginatedResponse<PaperResponse> listPapers(String titleSearch, PaperWindowFilter windowFilter, int limit, int offset) {
        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<Paper> paperPage = paperRepository.findByFilters(
                titleSearch, windowFilter == null ? null : windowFilter.name(), LocalDate.now(), pageable);

        List<PaperResponse> items = paperPage.getContent().stream()
                .map(paperMapper::toResponse)
                .toList();

        return new PaginatedResponse<>(items, paperPage.getTotalElements());
    }

    /**
     * Retrieves a paginated list of papers whose leaderboard is currently published — backs
     * student-facing leaderboard discovery, since students otherwise have no way to browse
     * papers at all.
     *
     * @param limit maximum number of results per page
     * @param offset number of results to skip
     * @return paginated response containing paper list and total count
     */
    public PaginatedResponse<PaperResponse> listPublishedLeaderboardPapers(int limit, int offset) {
        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<Paper> paperPage = paperRepository.findWithPublishedLeaderboard(pageable);

        List<PaperResponse> items = paperPage.getContent().stream()
                .map(paperMapper::toResponse)
                .toList();

        return new PaginatedResponse<>(items, paperPage.getTotalElements());
    }

    /**
     * Retrieves a paper by its ID.
     *
     * @param id the paper ID
     * @return the paper details
     * @throws ResourceNotFoundException if no paper exists with the given ID
     */
    public PaperResponse getPaperById(UUID id) {
        Paper paper = paperRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + id));

        return paperMapper.toResponse(paper);
    }

    /**
     * Creates a new paper.
     *
     * @param request the paper creation request
     * @param adminId the ID of the admin creating the paper
     * @return the created paper
     * @throws ValidationException if the validity window is invalid
     * @throws ResourceNotFoundException if any linked portal ID does not exist
     */
    @Transactional
    public PaperResponse createPaper(PaperCreateRequest request, UUID adminId) {
        validateDateRange(request.getStartDate(), request.getEndDate());

        Paper paper = new Paper();
        paper.setTitle(request.getTitle());
        paper.setDescription(request.getDescription());
        paper.setStartDate(request.getStartDate());
        paper.setEndDate(request.getEndDate());
        paper.setCreatedByAdminId(adminId);
        paper.setLinkedPortals(resolvePortals(request.getLinkedPortalIds()));

        if (request.getCorrelationId() != null) {
            paper.setCorrelation(requireCorrelation(request.getCorrelationId()));
        }

        if (request.getMarkScheme() != null) {
            validateMarkSchemeShape(request.getMarkScheme());
            // Applied to the correlation when there is one, so the sittings of a paper share a
            // single scheme. Joining a correlation that already has one with a *different* scheme
            // is refused rather than silently overwriting what the other sitting is marked against.
            MarkOwner owner = markOwnerResolver.ownerOf(paper);
            requireMarkSchemeCompatible(owner, request.getMarkScheme());
            applyMarkScheme(owner, request.getMarkScheme());
        }

        Paper savedPaper = paperRepository.save(paper);
        paperAuditService.record(savedPaper.getId(), savedPaper.getCorrelation(), PaperAuditAction.PAPER_CREATED,
                String.format("'%s' %s..%s, portals: %s%s", savedPaper.getTitle(),
                        savedPaper.getStartDate(), savedPaper.getEndDate(), describePortals(savedPaper),
                        savedPaper.getCorrelation() == null
                                ? ""
                                : ", correlation: " + savedPaper.getCorrelation().getCode()),
                adminId);
        calendarEventService.createPaperEvent(savedPaper);
        return paperMapper.toResponse(savedPaper);
    }

    /**
     * Sets or replaces a paper's mark scheme (which sections are enabled and their max marks).
     *
     * @param paperId the paper ID
     * @param scheme the new mark scheme; at least one of its three fields must be non-null
     * @return the updated paper
     * @throws ResourceNotFoundException if no paper exists with the given ID
     * @throws ValidationException if the scheme has no section enabled, or if the paper
     *         already has marks recorded (the scheme locks once any mark exists — delete all
     *         marks for the paper first to unlock it)
     */
    @Transactional
    public PaperResponse updateMarkScheme(UUID paperId, MarkSchemeDto scheme, UUID actorId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

        validateMarkSchemeShape(scheme);

        // Counted and applied against whatever owns this paper-event's grading. For a paper-event
        // in a correlation that is the correlation, so the scheme is shared by the sittings rather
        // than duplicated per sitting - and, critically, the lock can't be sidestepped by editing
        // the scheme through the sibling paper-event, which has no marks of its own.
        MarkOwner owner = markOwnerResolver.ownerOf(paper);

        long existingMarkCount = paperMarkRepository.countByOwnerId(owner.getId());
        if (existingMarkCount > 0) {
            throw ValidationException.markSchemeLocked(paperId, existingMarkCount);
        }

        applyMarkScheme(owner, scheme);

        Paper updatedPaper = paperRepository.save(paper);
        paperAuditService.record(paperId, paper.getCorrelation(), PaperAuditAction.MARK_SCHEME_CHANGED,
                String.format("mcq=%s, structured=%s, essay=%s%s",
                        scheme.getMcqMaxMarks(), scheme.getStructuredMaxMarks(), scheme.getEssayMaxMarks(),
                        paper.getCorrelation() == null ? "" : " (shared by " + paper.getCorrelation().getCode() + ")"),
                actorId);
        return paperMapper.toResponse(updatedPaper);
    }

    /**
     * Partially updates an existing paper. Only non-null fields are applied.
     * A present-but-empty {@code linkedPortalIds} is rejected; omitted leaves links untouched.
     *
     * @param paperId the paper ID to update
     * @param request the update request containing fields to modify
     * @return the updated paper
     * @throws ResourceNotFoundException if no paper exists with the given ID, or a linked portal ID does not exist
     * @throws ValidationException if the resulting validity window is invalid, or linkedPortalIds is present but empty
     */
    @Transactional
    public PaperResponse updatePaper(UUID paperId, PaperUpdateRequest request, UUID actorId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

        // Captured before anything is applied, so the trail can say what actually changed rather
        // than just what was sent.
        LocalDate previousStart = paper.getStartDate();
        LocalDate previousEnd = paper.getEndDate();
        String previousPortals = describePortals(paper);
        PaperCorrelation previousCorrelation = paper.getCorrelation();

        if (request.getTitle() != null) {
            paper.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            paper.setDescription(request.getDescription());
        }
        if (request.getStartDate() != null) {
            paper.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            paper.setEndDate(request.getEndDate());
        }
        validateDateRange(paper.getStartDate(), paper.getEndDate());

        if (request.getLinkedPortalIds() != null) {
            if (request.getLinkedPortalIds().isEmpty()) {
                throw new ValidationException("linkedPortalIds cannot be empty; a paper must always have at least one linked portal");
            }
            paper.setLinkedPortals(resolvePortals(request.getLinkedPortalIds()));
        }

        if (request.getCorrelationId() != null) {
            applyCorrelationChange(paper, requireCorrelation(request.getCorrelationId()), actorId);
        }

        Paper updatedPaper = paperRepository.save(paper);
        recordWindowAndPortalChanges(updatedPaper, previousStart, previousEnd, previousPortals,
                previousCorrelation, actorId);
        calendarEventService.syncPaperDates(paperId, updatedPaper.getStartDate(), updatedPaper.getEndDate());
        return paperMapper.toResponse(updatedPaper);
    }

    /**
     * Records the two changes that quietly alter who is entitled to sit a paper: moving its window,
     * and re-pointing which payments unlock it. Both are written only when the value actually
     * differs, so the trail stays readable rather than one entry per save.
     */
    private void recordWindowAndPortalChanges(Paper paper, LocalDate previousStart, LocalDate previousEnd,
                                              String previousPortals, PaperCorrelation previousCorrelation,
                                              UUID actorId) {
        if (!previousStart.equals(paper.getStartDate()) || !previousEnd.equals(paper.getEndDate())) {
            paperAuditService.record(paper.getId(), paper.getCorrelation(), PaperAuditAction.DATES_CHANGED,
                    String.format("%s..%s -> %s..%s", previousStart, previousEnd,
                            paper.getStartDate(), paper.getEndDate()),
                    actorId);
        }

        String currentPortals = describePortals(paper);
        if (!previousPortals.equals(currentPortals)) {
            paperAuditService.record(paper.getId(), paper.getCorrelation(), PaperAuditAction.PORTALS_CHANGED,
                    String.format("%s -> %s", previousPortals, currentPortals), actorId);
        }

        // Detaching is only reachable here when a correlation change was rejected partway; an
        // attach records itself inside applyCorrelationChange, where the old value is still known.
        if (previousCorrelation != null && paper.getCorrelation() == null) {
            paperAuditService.record(paper.getId(), previousCorrelation, PaperAuditAction.CORRELATION_DETACHED,
                    String.format("left %s", previousCorrelation.getCode()), actorId);
        }
    }

    private String describePortals(Paper paper) {
        return paper.getLinkedPortals().stream()
                .map(PaymentPortal::getDisplayName)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /**
     * Deletes a paper, provided nothing depends on it yet.
     *
     * @param paperId the paper ID to delete
     * @throws ResourceNotFoundException if no paper exists with the given ID
     * @throws ValidationException if the paper has any paper slots and/or marks recorded
     *         against it — remove those first
     */
    @Transactional
    public void deletePaper(UUID paperId, UUID actorId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

        // countByPaperId, not countByOwnerId: a paper-event in a correlation holds no marks of its
        // own, and the correlation's marks belong to the other sitting too - they are not this
        // paper-event's dependents and must not block deleting it.
        if (paperSlotRepository.existsByPaperId(paperId) || paperMarkRepository.countByPaperId(paperId) > 0) {
            throw ValidationException.paperHasDependents(paperId);
        }

        // Recorded before the delete, while the paper's own details are still readable - and kept
        // afterwards, since the trail holds bare ids rather than foreign keys precisely so it
        // outlives what it describes.
        paperAuditService.record(paperId, paper.getCorrelation(), PaperAuditAction.PAPER_DELETED,
                String.format("'%s' %s..%s", paper.getTitle(), paper.getStartDate(), paper.getEndDate()),
                actorId);
        paperRepository.delete(paper);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ValidationException("Paper start date must not be after its end date");
        }
    }

    /**
     * Validates that a mark scheme has at least one section enabled. The three sections are
     * otherwise independent of each other — no sum-to-100 or similar cross-field rule applies.
     */
    private void validateMarkSchemeShape(MarkSchemeDto scheme) {
        if (scheme.getMcqMaxMarks() == null && scheme.getStructuredMaxMarks() == null && scheme.getEssayMaxMarks() == null) {
            throw ValidationException.markSchemeRequiresAtLeastOneSection();
        }
    }

    private PaperCorrelation requireCorrelation(UUID correlationId) {
        return correlationRepository.findById(correlationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Correlation not found with id: " + correlationId));
    }

    /**
     * Moves a paper-event into a correlation, refusing when doing so would rewrite history.
     *
     * <p>Three things block it:
     * <ul>
     *   <li><b>It has already started.</b> Students may already be sitting it, and the change
     *       decides who gets a QR code for what.</li>
     *   <li><b>It has marks of its own.</b> Those belong to this paper-event; joining a
     *       correlation moves grading to the correlation and would strand them, invisible.</li>
     *   <li><b>A student would end up holding two slots for one paper.</b> See
     *       {@link #requireNoSlotCollision} — the admin is told who, and cancels one.</li>
     * </ul>
     *
     * <p>Note this only ever moves a paper-event <em>into</em> a correlation. Removing it from one
     * without naming another isn't reachable from here: {@code updatePaper} calls this only when a
     * correlation id was given, since an absent id means "leave this alone" rather than "remove it".
     *
     * <p>Existing slots are no obstacle — they are brought along. A slot records its correlation
     * when it is created, and that stamp is what the duplicate-QR check searches on, so slots
     * predating the move are restamped here rather than left invisible to it. That is what makes
     * tagging safe at any point, including as a repair once payments are already flowing.
     */
    private void applyCorrelationChange(Paper paper, PaperCorrelation correlation, UUID actorId) {
        if (correlation.equals(paper.getCorrelation())) {
            return;
        }
        if (!paper.getStartDate().isAfter(LocalDate.now())) {
            throw ValidationException.correlationChangeNotAllowed(paper.getId(), "it has already started");
        }
        if (paperMarkRepository.countByPaperId(paper.getId()) > 0) {
            throw ValidationException.correlationChangeNotAllowed(paper.getId(), "it already has marks of its own");
        }

        requireNoSlotCollision(paper, correlation);

        PaperCorrelation previous = paper.getCorrelation();
        paper.setCorrelation(correlation);
        paperRepository.saveAndFlush(paper);
        int restamped = paperSlotRepository.stampCorrelationOnSlots(paper.getId(), correlation);

        paperAuditService.record(paper.getId(), correlation, PaperAuditAction.CORRELATION_ATTACHED,
                String.format("%s -> %s, %d existing slot(s) brought along",
                        previous == null ? "none" : previous.getCode(), correlation.getCode(), restamped),
                actorId);
    }

    /**
     * Refuses the move when a student would end up holding two slots for one paper.
     *
     * <p>Only possible where they already do: they paid both months and the other sitting was
     * already grouped, so they were issued two QR codes before this grouping could stop it. The
     * database won't let one student hold two slots in a correlation, and nothing here should
     * quietly delete a code a student may already be carrying — so the admin is told exactly who
     * is affected and decides which to cancel.
     */
    private void requireNoSlotCollision(Paper paper, PaperCorrelation correlation) {
        List<PaperSlot> colliding =
                paperSlotRepository.findSlotsCollidingWithCorrelation(paper.getId(), correlation.getId());
        if (colliding.isEmpty()) {
            return;
        }

        String students = colliding.stream()
                .map(slot -> {
                    StudentSnapshot snapshot = slot.getPaymentSubmission().getStudentSnapshot();
                    return snapshot.getCodeNumber() == null
                            ? snapshot.getFullName()
                            : String.format("%s (%s)", snapshot.getFullName(), snapshot.getCodeNumber());
                })
                .collect(Collectors.joining(", "));

        throw ValidationException.correlationSlotCollision(correlation.getCode(), colliding.size(), students);
    }

    /**
     * Refuses a mark scheme that disagrees with the one its correlation already carries. The
     * scheme belongs to the correlation, so the two sittings of a paper cannot be marked out of
     * different totals; an empty scheme on the correlation means this is simply the first to set it.
     */
    private void requireMarkSchemeCompatible(MarkOwner owner, MarkSchemeDto scheme) {
        if (!(owner instanceof PaperCorrelation correlation)) {
            return;
        }
        boolean correlationHasScheme = correlation.getMcqMaxMarks() != null
                || correlation.getStructuredMaxMarks() != null
                || correlation.getEssayMaxMarks() != null;
        if (!correlationHasScheme) {
            return;
        }

        boolean same = sameMaxMark(correlation.getMcqMaxMarks(), scheme.getMcqMaxMarks())
                && sameMaxMark(correlation.getStructuredMaxMarks(), scheme.getStructuredMaxMarks())
                && sameMaxMark(correlation.getEssayMaxMarks(), scheme.getEssayMaxMarks());
        if (!same) {
            throw ValidationException.correlationMarkSchemeMismatch(correlation.getCode());
        }
    }

    /**
     * Compares two max marks by value, not by representation.
     *
     * <p>{@code BigDecimal.equals} also compares scale, so the 40.000 that comes back from a
     * {@code numeric(9,3)} column is "different" from the 40 an admin typed. That would reject a
     * sitting joining with the very scheme it is meant to share — most obviously when a paper-event
     * is duplicated, since the copy arrives prefilled with the original's marks.
     */
    private boolean sameMaxMark(BigDecimal stored, BigDecimal submitted) {
        if (stored == null || submitted == null) {
            return stored == submitted;
        }
        return stored.compareTo(submitted) == 0;
    }

    private void applyMarkScheme(MarkOwner owner, MarkSchemeDto scheme) {
        owner.setMcqMaxMarks(scheme.getMcqMaxMarks());
        owner.setStructuredMaxMarks(scheme.getStructuredMaxMarks());
        owner.setEssayMaxMarks(scheme.getEssayMaxMarks());
    }

    private List<PaymentPortal> resolvePortals(List<UUID> portalIds) {
        List<UUID> distinctIds = portalIds.stream().distinct().toList();
        List<PaymentPortal> portals = portalRepository.findAllById(distinctIds);

        if (portals.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("One or more linked portal IDs not found");
        }

        return portals;
    }

}
