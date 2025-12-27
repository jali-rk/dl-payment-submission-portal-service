package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionCreateRequest;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionResponse;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionStatusUpdateRequest;
import dopaminelite.payment_portal.dto.submission.UploadedFileRefDto;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.UploadedFile;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.PaymentSubmissionMapper;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepositoryLoggingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing payment submission business logic.
 * Handles submission creation, retrieval, status updates, and validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentSubmissionService {
    
    private final PaymentSubmissionRepository submissionRepository;
    private final PaymentPortalRepository portalRepository;
    private final PaymentSubmissionMapper submissionMapper;
    
    /**
     * Creates a new payment submission for a specific portal.
     * Validates that the portal exists and that the portal name confirmation matches.
     *
     * @param portalId the ID of the portal to submit to
     * @param request the submission request containing student ID, portal name confirmation, and files
     * @return the created submission with PENDING status
     * @throws ResourceNotFoundException if the portal does not exist
     * @throws ValidationException if portal name confirmation does not match
     */
    @Transactional
    public PaymentSubmissionResponse createSubmission(UUID portalId, PaymentSubmissionCreateRequest request) {
        PaymentPortal portal = portalRepository.findById(portalId)
                .orElseThrow(() -> ResourceNotFoundException.portalNotFound(portalId));
        
        // Validate portal name confirmation
        if (!portal.getName().equals(request.getPortalNameConfirmation())) {
            throw ValidationException.portalNameMismatch(portal.getName(), request.getPortalNameConfirmation());
        }
        
        PaymentSubmission submission = new PaymentSubmission();
        submission.setStudentId(request.getStudentId());
        submission.setPortal(portal);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setPortalNameAtSubmission(portal.getName());
        
        // Create uploaded file entities
        List<UploadedFile> files = request.getFiles().stream()
                .map(dto -> createUploadedFile(dto, submission))
                .collect(Collectors.toList());
        submission.setUploadedFiles(files);
        
        PaymentSubmission savedSubmission = submissionRepository.save(submission);
        return submissionMapper.toResponse(savedSubmission);
    }
    
    private UploadedFile createUploadedFile(UploadedFileRefDto dto, PaymentSubmission submission) {
        UploadedFile file = new UploadedFile();
        file.setFileId(dto.getFileId());
        file.setFileName(dto.getFileName());
        file.setFileType(dto.getFileType());
        file.setSubmission(submission);
        return file;
    }
    
    /**
     * Retrieves a paginated list of payment submissions with optional filtering.
     *
     * @param studentId filter by student ID, null for no filtering
     * @param portalId filter by portal ID, null for no filtering
     * @param status filter by submission status, null for no filtering
     * @param fromDate filter submissions from this date (inclusive), null for no filtering
     * @param toDate filter submissions until this date (inclusive), null for no filtering
     * @param limit maximum number of results per page
     * @param offset number of results to skip
     * @return paginated response containing submission list and total count
     */
    public PaginatedResponse<PaymentSubmissionResponse> listSubmissions(
            UUID studentId,
            UUID portalId,
            SubmissionStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Integer month,
            Integer year,
            int limit,
            int offset
    ) {
        log.debug("[SERVICE] listSubmissions called with - studentId: {}, portalId: {}, status: {}, fromDate: {}, toDate: {}, month: {}, year: {}, limit: {}, offset: {}",
                studentId, portalId, status, fromDate, toDate, month, year, limit, offset);
        
        // Validate month/year inputs
        if (month != null && (month < 1 || month > 12)) {
            log.warn("[SERVICE] Invalid month value: {}", month);
            throw new ValidationException("Month must be between 1 and 12");
        }
        if (month != null && year == null) {
            log.warn("[SERVICE] Month provided without year");
            throw new ValidationException("Year must be provided when month is specified");
        }

        // JPQL query has ORDER BY, so we don't need Sort in Pageable
        Pageable pageable = PageRequest.of(offset / limit, limit);
        log.debug("[SERVICE] Pageable created - page: {}, size: {}", offset / limit, limit);

        log.info("[SERVICE] Calling repository.findByFilters - studentId: {}, portalId: {}, status: {}, month: {}, year: {}",
                studentId, portalId, status, month, year);

        Page<PaymentSubmission> submissionPage = submissionRepository.findByFilters(
                studentId, portalId, status, month, year, pageable
        );
        
        log.info("[SERVICE] Repository query executed - total elements: {}, current page size: {}, total pages: {}",
                submissionPage.getTotalElements(), submissionPage.getContent().size(), submissionPage.getTotalPages());
        
        List<PaymentSubmissionResponse> items = submissionPage.getContent()
                .stream()
                .map(submissionMapper::toResponse)
                .toList();
        
        log.info("[SERVICE] Mapped submissions to response DTOs - count: {}", items.size());
        return new PaginatedResponse<>(items, submissionPage.getTotalElements());
    }
    
    /**
     * Retrieves a payment submission by its ID.
     *
     * @param submissionId the submission ID
     * @return the submission details including uploaded files
     * @throws ResourceNotFoundException if no submission exists with the given ID
     */
    public PaymentSubmissionResponse getSubmissionById(UUID submissionId) {
        PaymentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment submission not found with id: " + submissionId));
        
        return submissionMapper.toResponse(submission);
    }
    
    /**
     * Updates the status of a payment submission.
     * Validates that rejection reason is provided when status is REJECTED.
     *
     * @param submissionId the submission ID to update
     * @param request the status update request containing new status and optional rejection reason
     * @return the updated submission
     * @throws ResourceNotFoundException if no submission exists with the given ID
     * @throws ValidationException if status is REJECTED but rejection reason is missing
     */
    @Transactional
    public PaymentSubmissionResponse updateSubmissionStatus(UUID submissionId, PaymentSubmissionStatusUpdateRequest request) {
        PaymentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment submission not found with id: " + submissionId));
        
        // Validate rejection reason
        if (request.getStatus() == SubmissionStatus.REJECTED) {
            if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
                throw ValidationException.rejectionReasonRequired();
            }
        }
        
        submission.setStatus(request.getStatus());
        submission.setRejectionReason(request.getRejectionReason());
        
        PaymentSubmission updatedSubmission = submissionRepository.save(submission);
        return submissionMapper.toResponse(updatedSubmission);
    }
    
}
