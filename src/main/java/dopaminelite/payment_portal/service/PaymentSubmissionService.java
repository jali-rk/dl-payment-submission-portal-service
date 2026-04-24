package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionCreateRequest;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionResponse;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionStatusUpdateRequest;
import dopaminelite.payment_portal.dto.submission.UploadedFileRefDto;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.StudyPack;
import dopaminelite.payment_portal.entity.StudyPackPurchase;
import dopaminelite.payment_portal.entity.UploadedFile;
import dopaminelite.payment_portal.entity.enums.PurchaseStatus;
import dopaminelite.payment_portal.entity.enums.StudyMedium;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.PaymentSubmissionMapper;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepositoryLoggingUtil;
import dopaminelite.payment_portal.repository.StudyPackPurchaseRepository;
import dopaminelite.payment_portal.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final PaperCenterService paperCenterService;
    private final StudyPackRepository studyPackRepository;
    private final StudyPackPurchaseRepository studyPackPurchaseRepository;
    private final EnrollmentService enrollmentService;
    
    /**
     * Creates a new payment submission for a specific portal or study pack.
     * Validates that the portal/study pack exists and that the name confirmation matches.
     *
     * @param entityId the ID of the portal or study pack to submit to
     * @param request the submission request containing student ID, name confirmation, and files
     * @return the created submission with PENDING status
     * @throws ResourceNotFoundException if the portal or study pack does not exist
     * @throws ValidationException if name confirmation does not match
     */
    @Transactional
    public PaymentSubmissionResponse createSubmission(UUID entityId, PaymentSubmissionCreateRequest request) {
        String submissionType = request.getType() != null ? request.getType() : "PORTAL";
        
        PaymentSubmission submission = new PaymentSubmission();
        submission.setStudentId(request.getStudentId());
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setStudentSnapshot(submissionMapper.toStudentSnapshotEntity(request.getStudentSnapshot()));
        submission.setSubmissionType(submissionType);
        
        if ("STUDY_PACK".equals(submissionType)) {
            // Fetch study pack
            StudyPack studyPack = studyPackRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("Study pack not found with id: " + entityId));
            
            // Validate name confirmation
            if (!studyPack.getName().equals(request.getPortalNameConfirmation())) {
                throw ValidationException.portalNameMismatch(studyPack.getName(), request.getPortalNameConfirmation());
            }
            
            submission.setStudyPack(studyPack);
            submission.setPortalNameAtSubmission(studyPack.getName());
            submission.setPortal(null); // No portal for study pack submissions
            
            // Create PENDING StudyPackPurchase record immediately
            StudyPackPurchase purchase = new StudyPackPurchase();
            purchase.setStudentId(request.getStudentId());
            purchase.setStudyPack(studyPack);
            purchase.setAmount(studyPack.getPrice());
            purchase.setCurrency("LKR");
            purchase.setPurchaseStatus(PurchaseStatus.PENDING);
            purchase.setPaymentGateway("MANUAL_BANK_SLIP");
            purchase.setEnrollmentCompleted(false);
            studyPackPurchaseRepository.save(purchase);
            log.info("Created PENDING StudyPackPurchase record for student {} and study pack {}",
                    request.getStudentId(), studyPack.getId());
            
        } else {
            // Fetch portal
            PaymentPortal portal = portalRepository.findById(entityId)
                    .orElseThrow(() -> ResourceNotFoundException.portalNotFound(entityId));
            
            // Validate portal name confirmation
            if (!portal.getDisplayName().equals(request.getPortalNameConfirmation())) {
                throw ValidationException.portalNameMismatch(portal.getDisplayName(), request.getPortalNameConfirmation());
            }
            
            submission.setPortal(portal);
            submission.setPortalNameAtSubmission(portal.getDisplayName());
        }

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
     * @param month filter by portal's month (1-12), null for no filtering
     * @param year filter by portal's year, null for no filtering
     * @param studyMedium filter by student's study medium, null for no filtering
     * @param paperCenterId filter by paper center ID, null for no filtering
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
            Integer month,
            Integer year,
            StudyMedium studyMedium,
            String paperCenterId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            int limit,
            int offset
    ) {
        log.debug("[SERVICE] listSubmissions called with - studentId: {}, portalId: {}, status: {}, month: {}, year: {}, studyMedium: {}, paperCenterId: {}, fromDate: {}, toDate: {}, limit: {}, offset: {}",
                studentId, portalId, status, month, year, studyMedium, paperCenterId, fromDate, toDate, limit, offset);
        
        // Validate month/year inputs
        if (month != null && (month < 1 || month > 12)) {
            log.warn("[SERVICE] Invalid month value: {}", month);
            throw new ValidationException("Month must be between 1 and 12");
        }

        // JPQL query has ORDER BY, so we don't need Sort in Pageable
        Pageable pageable = PageRequest.of(offset / limit, limit);
        log.debug("[SERVICE] Pageable created - page: {}, size: {}", offset / limit, limit);

        // WORKAROUND: Some legacy rows have the paper center name stored in the ID column instead of the
        // actual UUID (upstream bug in BFF/User Service). This is not ideal as it adds an extra BFF call per
        // request, but there is no alternative at the given time since the existing data cannot be corrected.
        // TODO: Remove once upstream ensures paperCenterId always contains a valid UUID.
        String paperCenterName = null;
        if (paperCenterId != null) {
            Map<String, String> paperCenterNameMap = paperCenterService.getPaperCenterNameMap();
            paperCenterName = paperCenterNameMap.get(paperCenterId);
            log.debug("[SERVICE] Resolved paperCenterId '{}' to name '{}'", paperCenterId, paperCenterName);
        }

        log.info("[SERVICE] Calling repository.findByAdminFilters - studentId: {}, portalId: {}, status: {}, month: {}, year: {}, studyMedium: {}, paperCenterId: {}, paperCenterName: {}, fromDate: {}, toDate: {}",
                studentId, portalId, status, month, year, studyMedium, paperCenterId, paperCenterName, fromDate, toDate);

        Page<PaymentSubmission> submissionPage = submissionRepository.findByAdminFilters(
                studentId, portalId, status, month, year, studyMedium, paperCenterId, paperCenterName, fromDate, toDate, pageable
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
     * When approving a study pack submission, enrolls the student in all classes.
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
        
        // Handle approval logic
        if (request.getStatus() == SubmissionStatus.APPROVED && submission.getStatus() != SubmissionStatus.APPROVED) {
            handleApproval(submission);
        }
        
        // Handle rejection logic for study pack submissions
        if (request.getStatus() == SubmissionStatus.REJECTED && "STUDY_PACK".equals(submission.getSubmissionType())) {
            handleRejection(submission);
        }
        
        submission.setStatus(request.getStatus());
        submission.setRejectionReason(request.getRejectionReason());
        
        PaymentSubmission updatedSubmission = submissionRepository.save(submission);
        return submissionMapper.toResponse(updatedSubmission);
    }
    
    /**
     * Handles the approval logic for a submission.
     * For regular portal submissions, enrolls student in single class.
     * For study pack submissions, enrolls student in all classes in the pack AND creates a StudyPackPurchase record.
     *
     * @param submission the submission being approved
     * @throws RuntimeException if any enrollment fails (triggers transaction rollback)
     */
    private void handleApproval(PaymentSubmission submission) {
        if ("STUDY_PACK".equals(submission.getSubmissionType())) {
            // Study pack submission - enroll in multiple classes AND create purchase record
            StudyPack studyPack = submission.getStudyPack();
            if (studyPack == null) {
                log.error("Study pack is null for study pack submission: {}", submission.getId());
                throw new ValidationException("Study pack reference is missing for this submission");
            }
            
            log.info("Approving study pack submission {} - enrolling student {} in {} classes",
                    submission.getId(), submission.getStudentId(), studyPack.getClassIds().size());
            
            // Enroll student in all classes
            for (String classId : studyPack.getClassIds()) {
                try {
                    enrollmentService.enrollStudent(submission.getStudentId(), classId);
                    log.info("Successfully enrolled student {} in class {}", submission.getStudentId(), classId);
                } catch (Exception e) {
                    log.error("Failed to enroll student {} in class {}", submission.getStudentId(), classId, e);
                    throw new RuntimeException("Failed to enroll student in class " + classId + ": " + e.getMessage(), e);
                }
            }
            
            log.info("Successfully enrolled student {} in all {} classes from study pack {}",
                    submission.getStudentId(), studyPack.getClassIds().size(), studyPack.getId());
            
            // Update existing StudyPackPurchase record from PENDING to COMPLETED
            try {
                StudyPackPurchase purchase = studyPackPurchaseRepository
                        .findByStudentIdAndStudyPackId(submission.getStudentId(), studyPack.getId())
                        .orElseThrow(() -> new RuntimeException("Purchase record not found for student " + 
                                submission.getStudentId() + " and study pack " + studyPack.getId()));
                
                purchase.setPurchaseStatus(PurchaseStatus.COMPLETED);
                purchase.setTransactionId(submission.getId().toString()); // Use submission ID as transaction reference
                purchase.setEnrollmentCompleted(true);
                purchase.setEnrollmentError(null);
                
                studyPackPurchaseRepository.save(purchase);
                log.info("Updated StudyPackPurchase record to COMPLETED for student {} and study pack {}",
                        submission.getStudentId(), studyPack.getId());
            } catch (Exception e) {
                log.error("Failed to update StudyPackPurchase record", e);
                throw new RuntimeException("Failed to update purchase record: " + e.getMessage(), e);
            }
        } else {
            // Regular portal submission - no automatic enrollment for portals
            log.info("Approved regular portal submission {} for student {}", 
                    submission.getId(), submission.getStudentId());
        }
    }
    
    /**
     * Handles the rejection logic for a study pack submission.
     * Updates the purchase status to FAILED.
     *
     * @param submission the submission being rejected
     */
    private void handleRejection(PaymentSubmission submission) {
        StudyPack studyPack = submission.getStudyPack();
        if (studyPack == null) {
            log.warn("Study pack is null for study pack submission: {}", submission.getId());
            return;
        }
        
        log.info("Rejecting study pack submission {} - updating purchase status to FAILED",
                submission.getId());
        
        try {
            Optional<StudyPackPurchase> purchaseOpt = studyPackPurchaseRepository
                    .findByStudentIdAndStudyPackId(submission.getStudentId(), studyPack.getId());
            
            if (purchaseOpt.isPresent()) {
                StudyPackPurchase purchase = purchaseOpt.get();
                purchase.setPurchaseStatus(PurchaseStatus.FAILED);
                purchase.setEnrollmentError("Payment submission was rejected");
                studyPackPurchaseRepository.save(purchase);
                log.info("Updated StudyPackPurchase record to FAILED for student {} and study pack {}",
                        submission.getStudentId(), studyPack.getId());
            } else {
                log.warn("No purchase record found for rejected submission {}", submission.getId());
            }
        } catch (Exception e) {
            log.error("Failed to update StudyPackPurchase record on rejection", e);
            // Don't throw - rejection should still proceed even if purchase update fails
        }
    }
    
}
