package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionCreateRequest;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionResponse;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionStatusUpdateRequest;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.service.PaymentSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for managing payment submissions.
 * Provides endpoints for creating, retrieving, and updating payment submissions.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentSubmissionController {
    
    private final PaymentSubmissionService submissionService;
    
    /**
     * Creates a new payment submission for a specific portal.
     *
     * @param portalId the unique identifier of the portal to submit to
     * @param request the submission request containing studentId, portalNameConfirmation, and uploaded files
     * @return the created submission with HTTP 201 status
     * @throws ResourceNotFoundException if the portal does not exist
     * @throws ValidationException if portal name confirmation does not match
     */
    @PostMapping("/portals/{portalId}/submissions")
    public ResponseEntity<PaymentSubmissionResponse> createSubmission(
            @PathVariable UUID portalId,
            @Valid @RequestBody PaymentSubmissionCreateRequest request
    ) {
        PaymentSubmissionResponse response = submissionService.createSubmission(portalId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Lists payment submissions with optional filtering and pagination.
     *
     * @param studentId filter by student ID, optional
     * @param portalId filter by portal ID, optional
     * @param status filter by submission status (PENDING, APPROVED, REJECTED), optional
     * @param fromDate filter submissions from this date (inclusive), optional
     * @param toDate filter submissions until this date (inclusive), optional
     * @param limit maximum number of results per page, defaults to 10
     * @param offset number of results to skip, defaults to 0
     * @return paginated list of payment submissions
     */
    @GetMapping("/submissions")
    public ResponseEntity<PaginatedResponse<PaymentSubmissionResponse>> listSubmissions(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID portalId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        log.info("[CONTROLLER] Received GET /submissions request - studentId: {}, portalId: {}, status: {}, fromDate: {}, toDate: {}, month: {}, year: {}, limit: {}, offset: {}",
                studentId, portalId, status, fromDate, toDate, month, year, limit, offset);
        
        PaginatedResponse<PaymentSubmissionResponse> response = submissionService.listSubmissions(
            studentId, portalId, status, fromDate, toDate, month, year, limit, offset
        );
        
        log.info("[CONTROLLER] Successfully retrieved submissions - total count: {}, returned items: {}",
                response.getTotal(), response.getItems().size());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Retrieves a payment submission by its ID.
     *
     * @param submissionId the unique identifier of the submission
     * @return the submission details including uploaded files
     * @throws ResourceNotFoundException if no submission exists with the given ID
     */
    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<PaymentSubmissionResponse> getSubmissionById(@PathVariable UUID submissionId) {
        PaymentSubmissionResponse response = submissionService.getSubmissionById(submissionId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Updates the status of a payment submission.
     *
     * @param submissionId the unique identifier of the submission to update
     * @param request the status update request containing new status and optional rejection reason
     * @return the updated submission details
     * @throws ResourceNotFoundException if no submission exists with the given ID
     * @throws ValidationException if status is REJECTED but rejection reason is not provided
     */
    @PatchMapping("/submissions/{submissionId}/status")
    public ResponseEntity<PaymentSubmissionResponse> updateSubmissionStatus(
            @PathVariable UUID submissionId,
            @Valid @RequestBody PaymentSubmissionStatusUpdateRequest request
    ) {
        PaymentSubmissionResponse response = submissionService.updateSubmissionStatus(submissionId, request);
        return ResponseEntity.ok(response);
    }
    
}
