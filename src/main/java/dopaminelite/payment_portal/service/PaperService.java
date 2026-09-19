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
import dopaminelite.payment_portal.entity.PaymentPortal;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    public PaperResponse updateMarkScheme(UUID paperId, MarkSchemeDto scheme) {
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
    public PaperResponse updatePaper(UUID paperId, PaperUpdateRequest request) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

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
            applyCorrelationChange(paper, requireCorrelation(request.getCorrelationId()));
        }

        Paper updatedPaper = paperRepository.save(paper);
        calendarEventService.syncPaperDates(paperId, updatedPaper.getStartDate(), updatedPaper.getEndDate());
        return paperMapper.toResponse(updatedPaper);
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
    public void deletePaper(UUID paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));

        // countByPaperId, not countByOwnerId: a paper-event in a correlation holds no marks of its
        // own, and the correlation's marks belong to the other sitting too - they are not this
        // paper-event's dependents and must not block deleting it.
        if (paperSlotRepository.existsByPaperId(paperId) || paperMarkRepository.countByPaperId(paperId) > 0) {
            throw ValidationException.paperHasDependents(paperId);
        }

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
     *   <li><b>It has slots of its own.</b> A slot records its correlation when it is created, and
     *       that record is what stops a second QR being issued for the same paper. Slots created
     *       before the move carry no correlation, so they'd never be found — and the duplicate
     *       this feature exists to prevent would happen anyway.</li>
     * </ul>
     *
     * <p>Note the third condition is about <em>this paper-event's</em> slots, not the
     * correlation's. Joining a correlation that already has slots from its other sitting is the
     * normal case — it is exactly how the second sitting is added once the first month's payments
     * have started being approved.
     */
    private void applyCorrelationChange(Paper paper, PaperCorrelation correlation) {
        if (correlation.equals(paper.getCorrelation())) {
            return;
        }
        if (!paper.getStartDate().isAfter(LocalDate.now())) {
            throw ValidationException.correlationChangeNotAllowed(paper.getId(), "it has already started");
        }
        if (paperMarkRepository.countByPaperId(paper.getId()) > 0) {
            throw ValidationException.correlationChangeNotAllowed(paper.getId(), "it already has marks of its own");
        }
        if (paperSlotRepository.existsByPaperId(paper.getId())) {
            throw ValidationException.correlationChangeNotAllowed(
                    paper.getId(), "slots have already been issued for it");
        }

        paper.setCorrelation(correlation);
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

        boolean same = Objects.equals(correlation.getMcqMaxMarks(), scheme.getMcqMaxMarks())
                && Objects.equals(correlation.getStructuredMaxMarks(), scheme.getStructuredMaxMarks())
                && Objects.equals(correlation.getEssayMaxMarks(), scheme.getEssayMaxMarks());
        if (!same) {
            throw ValidationException.correlationMarkSchemeMismatch(correlation.getCode());
        }
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
