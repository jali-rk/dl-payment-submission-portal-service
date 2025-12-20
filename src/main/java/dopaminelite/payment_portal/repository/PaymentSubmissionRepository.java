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

@Repository
public interface PaymentSubmissionRepository extends JpaRepository<PaymentSubmission, UUID>, JpaSpecificationExecutor<PaymentSubmission> {
    
    Page<PaymentSubmission> findByStudentId(UUID studentId, Pageable pageable);
    
    Page<PaymentSubmission> findByPortalId(UUID portalId, Pageable pageable);
    
    Page<PaymentSubmission> findByStatus(SubmissionStatus status, Pageable pageable);
    
    @Query("SELECT s FROM PaymentSubmission s WHERE " +
           "(:studentId IS NULL OR s.studentId = :studentId) AND " +
           "(:portalId IS NULL OR s.portal.id = :portalId) AND " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:fromDate IS NULL OR DATE(s.submittedAt) >= :fromDate) AND " +
           "(:toDate IS NULL OR DATE(s.submittedAt) <= :toDate)")
    Page<PaymentSubmission> findByFilters(
            @Param("studentId") UUID studentId,
            @Param("portalId") UUID portalId,
            @Param("status") SubmissionStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
    
    List<PaymentSubmission> findByIdIn(List<UUID> submissionIds);
    
}
