package dopaminelite.payment_portal.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.portal.BulkPortalVisibilityUpdateRequest;
import dopaminelite.payment_portal.dto.portal.PaymentPortalCreateRequest;
import dopaminelite.payment_portal.dto.portal.PaymentPortalResponse;
import dopaminelite.payment_portal.dto.portal.PaymentPortalUpdateRequest;
import dopaminelite.payment_portal.service.PaymentPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.UUID;

/**
 * REST controller for managing payment portals.
 * Provides endpoints for creating, retrieving, updating, and managing payment portals.
 */
@RestController
@RequestMapping("/portals")
@RequiredArgsConstructor
public class PaymentPortalController {

    private final PaymentPortalService portalService;

    /**
     * Lists all payment portals with optional filtering and pagination.
     *
     * @param month filter by month (1-12), optional
     * @param year filter by year, optional
     * @param isPublished filter by published status, optional
     * @param limit maximum number of results per page, defaults to 10
     * @param offset number of results to skip, defaults to 0
     * @return paginated list of payment portals
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<PaymentPortalResponse>> listPortals(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        // Validate limit range
        if (limit < 1 || limit > 100) {
            limit = 20;
        }
        
        PaginatedResponse<PaymentPortalResponse> response = portalService.listPortals(
                month, year, isPublished, limit, offset
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new payment portal.
     *
     * @param request the portal creation request containing name, displayName, month, year, and visibility
     * @return the created portal with HTTP 201 status
     * @throws DuplicateResourceException if a portal with the same name already exists
     */
    @PostMapping
    public ResponseEntity<PaymentPortalResponse> createPortal(
            @Valid @RequestBody PaymentPortalCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID adminId = extractUserIdFromJwt(authorizationHeader)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User ID not found"));
        PaymentPortalResponse response = portalService.createPortal(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{portalId}")
    /**
     * Retrieves a payment portal by its unique identifier.
     *
     * @param portalId the UUID of the payment portal to retrieve
     * @return the payment portal details
     * @throws ResourceNotFoundException if a portal with the given ID does not exist
     */
    public ResponseEntity<PaymentPortalResponse> getPortalById(@PathVariable UUID portalId) {
        PaymentPortalResponse response = portalService.getPortalById(portalId);
        return ResponseEntity.ok(response);
    }
    
    @PatchMapping("/{portalId}")
    /**
     * Partially updates an existing payment portal.
     *
     * Supported fields include display name and visibility settings.
     *
     * @param portalId the UUID of the payment portal to update
     * @param request the update request payload containing fields to modify
     * @return the updated payment portal
     * @throws ResourceNotFoundException if a portal with the given ID does not exist
     */
    public ResponseEntity<PaymentPortalResponse> updatePortal(
            @PathVariable UUID portalId,
            @Valid @RequestBody PaymentPortalUpdateRequest request
    ) {
        PaymentPortalResponse response = portalService.updatePortal(portalId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates the visibility of multiple payment portals in a single operation.
     *
     * @param request the bulk update request containing portal IDs and new visibility setting
     * @return HTTP 204 No Content on success
     * @throws ResourceNotFoundException if any portal ID in the request does not exist
     */
    @PatchMapping("/bulk-visibility")
    public ResponseEntity<Void> bulkUpdateVisibility(
            @Valid @RequestBody BulkPortalVisibilityUpdateRequest request
    ) {
        portalService.bulkUpdateVisibility(request);
        return ResponseEntity.ok().build();
    }
    
    private java.util.Optional<UUID> extractUserIdFromJwt(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return java.util.Optional.empty();
        }
        String token = authorizationHeader.substring(7);
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return java.util.Optional.empty();
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(payloadBytes);
            String candidate = null;
            if (payload.hasNonNull("sub")) {
                candidate = payload.get("sub").asText();
            } else if (payload.hasNonNull("user_id")) {
                candidate = payload.get("user_id").asText();
            } else if (payload.hasNonNull("uid")) {
                candidate = payload.get("uid").asText();
            }
            if (candidate == null || candidate.isBlank()) {
                return java.util.Optional.empty();
            }
            try {
                return java.util.Optional.of(UUID.fromString(candidate));
            } catch (IllegalArgumentException e) {
                return java.util.Optional.of(UUID.nameUUIDFromBytes(candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }
}
