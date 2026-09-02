package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.paper.MarkSchemeDto;
import dopaminelite.payment_portal.dto.paper.PaperCreateRequest;
import dopaminelite.payment_portal.dto.paper.PaperResponse;
import dopaminelite.payment_portal.dto.paper.PaperUpdateRequest;
import dopaminelite.payment_portal.dto.paper.PaperWindowFilter;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.PaperMapper;
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
    private final PaperSlotRepository paperSlotRepository;
    private final PaperMapper paperMapper;

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

        if (request.getMarkScheme() != null) {
            validateMarkSchemeShape(request.getMarkScheme());
            applyMarkScheme(paper, request.getMarkScheme());
        }

        Paper savedPaper = paperRepository.save(paper);
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

        long existingMarkCount = paperMarkRepository.countByPaperId(paperId);
        if (existingMarkCount > 0) {
            throw ValidationException.markSchemeLocked(paperId, existingMarkCount);
        }

        applyMarkScheme(paper, scheme);

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

        Paper updatedPaper = paperRepository.save(paper);
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

    private void applyMarkScheme(Paper paper, MarkSchemeDto scheme) {
        paper.setMcqMaxMarks(scheme.getMcqMaxMarks());
        paper.setStructuredMaxMarks(scheme.getStructuredMaxMarks());
        paper.setEssayMaxMarks(scheme.getEssayMaxMarks());
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
