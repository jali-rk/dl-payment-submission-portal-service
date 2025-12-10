package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.portal.BulkPortalVisibilityUpdateRequest;
import dopaminelite.payment_portal.dto.portal.PaymentPortalCreateRequest;
import dopaminelite.payment_portal.dto.portal.PaymentPortalResponse;
import dopaminelite.payment_portal.dto.portal.PaymentPortalUpdateRequest;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import dopaminelite.payment_portal.exception.DuplicateResourceException;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.mapper.PaymentPortalMapper;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing payment portal business logic.
 * Handles portal creation, retrieval, updates, and bulk operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentPortalService {

    private final PaymentPortalRepository portalRepository;
    private final PaymentPortalMapper portalMapper;

    /**
     * Retrieves a paginated list of payment portals with optional filtering.
     *
     * @param month filter by month (1-12), null for no filtering
     * @param year filter by year, null for no filtering
     * @param isPublished filter by published status, null for no filtering
     * @param limit maximum number of results per page
     * @param offset number of results to skip
     * @return paginated response containing portal list and total count
     */
    public PaginatedResponse<PaymentPortalResponse> listPortals(
            Integer month,
            Integer year,
            Boolean isPublished,
            int limit,
            int offset
    ) {
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentPortal> portalPage;
        
        if (month != null && year != null) {
            portalPage = portalRepository.findByMonthAndYear(month, year, pageable);
        } else {
            portalPage = portalRepository.findAll(pageable);
        }
        
        List<PaymentPortalResponse> items = portalPage.getContent()
                .stream()
                .filter(portal -> isPublished == null || 
                        (isPublished && portal.getVisibility() == PortalVisibility.PUBLISHED) ||
                        (!isPublished && portal.getVisibility() != PortalVisibility.PUBLISHED))
                .map(portalMapper::toResponse)
                .toList();
        
        long total = portalPage.getTotalElements();
        
        return new PaginatedResponse<>(items, total);
    }
    
    /**
     * Creates a new payment portal.
     *
     * @param request the portal creation request
     * @param adminId the ID of the admin creating the portal
     * @return the created portal
     * @throws DuplicateResourceException if a portal with the same name already exists
     */
    @Transactional
    public PaymentPortalResponse createPortal(PaymentPortalCreateRequest request, UUID adminId) {
        // Check for duplicate portal name
        if (portalRepository.existsByName(request.getName())) {
            throw DuplicateResourceException.portalNameExists(request.getName());
        }
        
        PaymentPortal portal = new PaymentPortal();
        portal.setMonth(request.getMonth());
        portal.setYear(request.getYear());
        portal.setName(request.getName());
        portal.setDisplayName(request.getDisplayName());
        portal.setIsPublished(request.getIsPublished() != null ? request.getIsPublished() : false);
        portal.setVisibility(request.getIsPublished() != null && request.getIsPublished() 
            ? PortalVisibility.PUBLISHED 
            : PortalVisibility.HIDDEN);
        portal.setCreatedByAdminId(adminId);
        
        PaymentPortal savedPortal = portalRepository.save(portal);
        return portalMapper.toResponse(savedPortal);
    }
    
    /**
     * Retrieves a payment portal by its ID.
     *
     * @param id the portal ID
     * @return the portal details
     * @throws ResourceNotFoundException if no portal exists with the given ID
     */
    public PaymentPortalResponse getPortalById(UUID id) {
        PaymentPortal portal = portalRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.portalNotFound(id));
        
        return portalMapper.toResponse(portal);
    }
    
    /**
     * Updates an existing payment portal.
     * Only non-null fields in the request are updated.
     * Automatically synchronizes visibility and isPublished fields.
     *
     * @param portalId the portal ID to update
     * @param request the update request containing fields to modify
     * @return the updated portal
     * @throws ResourceNotFoundException if no portal exists with the given ID
     */
    @Transactional
    public PaymentPortalResponse updatePortal(UUID portalId, PaymentPortalUpdateRequest request) {
        PaymentPortal portal = portalRepository.findById(portalId)
                .orElseThrow(() -> ResourceNotFoundException.portalNotFound(portalId));
        
        if (request.getDisplayName() != null) {
            portal.setDisplayName(request.getDisplayName());
        }
        
        if (request.getIsPublished() != null) {
            portal.setIsPublished(request.getIsPublished());
            // Sync visibility with isPublished
            portal.setVisibility(request.getIsPublished() 
                ? PortalVisibility.PUBLISHED 
                : PortalVisibility.HIDDEN);
        }
        
        if (request.getVisibility() != null) {
            portal.setVisibility(request.getVisibility());
            // Sync isPublished with visibility
            portal.setIsPublished(request.getVisibility() == PortalVisibility.PUBLISHED);
        }
        
        PaymentPortal updatedPortal = portalRepository.save(portal);
        return portalMapper.toResponse(updatedPortal);
    }

    /**
     * Updates the visibility of multiple portals in a single transaction.
     *
     * @param request the bulk update request containing portal IDs and new visibility
     * @throws ResourceNotFoundException if any portal ID does not exist
     */
    @Transactional
    public void bulkUpdateVisibility(BulkPortalVisibilityUpdateRequest request) {
        List<PaymentPortal> portals = portalRepository.findAllById(request.getPortalIds());
        
        if (portals.size() != request.getPortalIds().size()) {
            throw new ResourceNotFoundException("One or more portal IDs not found");
        }
        
        for (PaymentPortal portal : portals) {
            portal.setIsPublished(request.getIsPublished());
            portal.setVisibility(request.getIsPublished() 
                ? PortalVisibility.PUBLISHED 
                : PortalVisibility.HIDDEN);
        }
        
        portalRepository.saveAll(portals);
    }
    
}
