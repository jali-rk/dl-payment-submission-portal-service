package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for PaymentSubmission entity operations.
 * Provides CRUD operations and custom queries for payment submission management.
 */
@Repository
public interface PaymentSubmissionRepository extends JpaRepository<PaymentSubmission, UUID>, JpaSpecificationExecutor<PaymentSubmission> {
    
    /**
     * Finds all submissions for a specific student with pagination.
     *
     * @param studentId the student's UUID
     * @param pageable pagination information
     * @return a page of submissions for the student
     */
    Page<PaymentSubmission> findByStudentId(UUID studentId, Pageable pageable);
    
    /**
     * Finds all submissions for a specific portal with pagination.
     *
     * @param portalId the portal's UUID
     * @param pageable pagination information
     * @return a page of submissions for the portal
     */
    Page<PaymentSubmission> findByPortalId(UUID portalId, Pageable pageable);
    
    /**
     * Finds all submissions with a specific status with pagination.
     *
     * @param status the submission status
     * @param pageable pagination information
     * @return a page of submissions with the given status
     */
    Page<PaymentSubmission> findByStatus(SubmissionStatus status, Pageable pageable);
    
    /**
     * Finds submissions with flexible filtering criteria using JPQL.
     * All parameters are optional (can be null) for flexible querying.
     *
     * @param studentId filter by student ID, null for no filtering
     * @param portalId filter by portal ID, null for no filtering
     * @param status filter by submission status, null for no filtering
     * @param month filter by portal's month (1-12), null for no filtering
     * @param year filter by portal's year, null for no filtering
     * @param pageable pagination information
     * @return a page of submissions matching the criteria
     */
    @Query("SELECT p FROM PaymentSubmission p WHERE " +
           "(:studentId IS NULL OR p.studentId = :studentId) AND " +
           "(:portalId IS NULL OR p.portal.id = :portalId) AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:month IS NULL OR p.portal.month = :month) AND " +
           "(:year IS NULL OR p.portal.year = :year) " +
           "ORDER BY p.submittedAt DESC")
    Page<PaymentSubmission> findByFilters(
            @Param("studentId") UUID studentId,
            @Param("portalId") UUID portalId,
            @Param("status") SubmissionStatus status,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
    );
    
    /**
     * Finds submissions by a list of submission IDs.
     *
     * @param submissionIds list of submission UUIDs
     * @return list of submissions with matching IDs
     */
    List<PaymentSubmission> findByIdIn(List<UUID> submissionIds);
    
}
