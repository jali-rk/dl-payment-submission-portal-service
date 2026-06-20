package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionCreateRequest;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionResponse;
import dopaminelite.payment_portal.dto.submission.PaymentSubmissionStatusUpdateRequest;
import dopaminelite.payment_portal.dto.submission.UploadedFileRefDto;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.UploadedFile;
import dopaminelite.payment_portal.entity.enums.StudyMedium;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        if (!portal.getDisplayName().equals(request.getPortalNameConfirmation())) {
            throw ValidationException.portalNameMismatch(portal.getDisplayName(), request.getPortalNameConfirmation());
        }

        // Prevent submission if student already has a PENDING or APPROVED submission for this portal
        boolean hasActiveSubmission = submissionRepository.findAll((root, query, cb) -> cb.and(
            cb.equal(root.get("studentId"), request.getStudentId()),
            cb.equal(root.get("portal").get("id"), portalId),
            root.get("status").in(SubmissionStatus.PENDING, SubmissionStatus.APPROVED)
        )).size() > 0;

        if(hasActiveSubmission) {
            log.error("Validation Error: Active submission already exists. studentId={}, portalId={}",
            request.getStudentId(),
            portalId
            );
            throw new RuntimeException("Validation Error: Cannot submit. A PENDING or APPROVED submission already exists for this portal.");
        }
        
        PaymentSubmission submission = new PaymentSubmission();
        submission.setStudentId(request.getStudentId());
        submission.setPortal(portal);
        submission.setStatus(SubmissionStatus.PENDING);
        submission.setPortalNameAtSubmission(portal.getDisplayName());
        submission.setStudentSnapshot(submissionMapper.toStudentSnapshotEntity(request.getStudentSnapshot()));

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
        // Validate month/year inputs
        if (month != null && (month < 1 || month > 12)) {
            throw new ValidationException("Month must be between 1 and 12");
        }

        Pageable pageable = PageRequest.of(offset / limit, limit);

        // WORKAROUND: Some legacy rows have the paper center name stored in the ID column instead of the
        // actual UUID (upstream bug in BFF/User Service). This is not ideal as it adds an extra BFF call per
        // request, but there is no alternative at the given time since the existing data cannot be corrected.
        // TODO: Remove once upstream ensures paperCenterId always contains a valid UUID.
        String paperCenterName = null;
        if (paperCenterId != null) {
            Map<String, String> paperCenterNameMap = paperCenterService.getPaperCenterNameMap();
            paperCenterName = paperCenterNameMap.get(paperCenterId);
        }

        Page<PaymentSubmission> submissionPage = submissionRepository.findByAdminFilters(
                studentId, portalId, status, month, year, studyMedium, paperCenterId, paperCenterName, fromDate, toDate, pageable
        );
        
        List<PaymentSubmissionResponse> items = submissionPage.getContent()
                .stream()
                .map(submissionMapper::toResponse)
                .toList();
        
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

    /**
     * Finds students who had APPROVED payments in ALL include portals but NOT in ANY of the exclude portals.
     * This identifies students who dropped out after being consistently active in all include portals.
     *
     * @param includePortalIds list of portal IDs - student must have APPROVED payment in ALL these portals
     * @param excludePortalIds list of portal IDs - student must NOT have APPROVED payment in ANY of these
     * @return list of student IDs who dropped out
     */
    public List<UUID> findDropoutStudents(
            List<UUID> includePortalIds,
            List<UUID> excludePortalIds
    ) {
        if (includePortalIds == null || includePortalIds.isEmpty()) {
            throw new ValidationException("At least one include portal must be provided");
        }
        
        if (excludePortalIds == null || excludePortalIds.isEmpty()) {
            throw new ValidationException("At least one exclude portal must be provided");
        }
        
        List<UUID> repositoryResult = submissionRepository.findDropoutStudentIds(
                includePortalIds,
                (long) includePortalIds.size(),
                excludePortalIds
        );
        
        log.debug("Found {} dropout students", repositoryResult.size());
        
        // Wrap in new ArrayList to avoid Hibernate proxy serialization issues
        return new ArrayList<>(repositoryResult);
    }
    
}
