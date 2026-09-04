package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaperSlotCreationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PaperSlotCreationResult entity operations.
 */
@Repository
public interface PaperSlotCreationResultRepository extends JpaRepository<PaperSlotCreationResult, UUID> {

    Optional<PaperSlotCreationResult> findByPaymentSubmissionIdAndPaperId(UUID paymentSubmissionId, UUID paperId);

    /**
     * Finds all creation-result rows for a submission, with paper/slot/submission/portal
     * eagerly fetched so the mapper can build full nested details without extra queries.
     *
     * @param paymentSubmissionId the submission's UUID
     * @return the current result row per linked paper, most recently attempted first
     */
    @Query("SELECT r FROM PaperSlotCreationResult r " +
           "JOIN FETCH r.paper " +
           "LEFT JOIN FETCH r.paperSlot ps " +
           "LEFT JOIN FETCH ps.paymentSubmission sub " +
           "LEFT JOIN FETCH sub.portal " +
           "WHERE r.paymentSubmission.id = :paymentSubmissionId " +
           "ORDER BY r.attemptedAt DESC")
    List<PaperSlotCreationResult> findByPaymentSubmissionId(@Param("paymentSubmissionId") UUID paymentSubmissionId);

}
