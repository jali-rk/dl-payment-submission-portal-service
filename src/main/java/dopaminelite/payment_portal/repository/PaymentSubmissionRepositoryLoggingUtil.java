package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Logging utility for PaymentSubmission repository operations.
 * Provides static methods to wrap repository calls with detailed logging.
 * This is NOT a Spring bean to avoid circular dependencies.
 */
@Slf4j
public class PaymentSubmissionRepositoryLoggingUtil {
    
    /**
     * Logs and executes findByStudentId query.
     */
    public static Page<PaymentSubmission> logFindByStudentId(
            PaymentSubmissionRepository repository,
            UUID studentId,
            Pageable pageable
    ) {
        long startTime = System.currentTimeMillis();
        
        log.debug("[REPO] Executing findByStudentId query - studentId={}, page={}, size={}",
                studentId, pageable.getPageNumber(), pageable.getPageSize());
        
        try {
            Page<PaymentSubmission> result = repository.findByStudentId(studentId, pageable);
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[REPO] findByStudentId completed in {}ms - Results: totalElements={}, pageSize={}",
                    duration, result.getTotalElements(), result.getContent().size());
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[REPO] findByStudentId failed after {}ms - Exception: {}", duration, e.getClass().getSimpleName(), e);
            throw e;
        }
    }
    
    /**
     * Logs and executes findByPortalId query.
     */
    public static Page<PaymentSubmission> logFindByPortalId(
            PaymentSubmissionRepository repository,
            UUID portalId,
            Pageable pageable
    ) {
        long startTime = System.currentTimeMillis();
        
        log.debug("[REPO] Executing findByPortalId query - portalId={}, page={}, size={}",
                portalId, pageable.getPageNumber(), pageable.getPageSize());
        
        try {
            Page<PaymentSubmission> result = repository.findByPortalId(portalId, pageable);
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[REPO] findByPortalId completed in {}ms - Results: totalElements={}, pageSize={}",
                    duration, result.getTotalElements(), result.getContent().size());
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[REPO] findByPortalId failed after {}ms - Exception: {}", duration, e.getClass().getSimpleName(), e);
            throw e;
        }
    }
    
    /**
     * Logs and executes findByStatus query.
     */
    public static Page<PaymentSubmission> logFindByStatus(
            PaymentSubmissionRepository repository,
            SubmissionStatus status,
            Pageable pageable
    ) {
        long startTime = System.currentTimeMillis();
        
        log.debug("[REPO] Executing findByStatus query - status={}, page={}, size={}",
                status, pageable.getPageNumber(), pageable.getPageSize());
        
        try {
            Page<PaymentSubmission> result = repository.findByStatus(status, pageable);
            
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[REPO] findByStatus completed in {}ms - Results: totalElements={}, pageSize={}",
                    duration, result.getTotalElements(), result.getContent().size());
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[REPO] findByStatus failed after {}ms - Exception: {}", duration, e.getClass().getSimpleName(), e);
            throw e;
        }
    }
}

