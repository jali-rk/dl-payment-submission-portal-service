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
     * The payments that each of these slots covers.
     *
     * <p>Normally one, but a slot extended across a correlation is the single QR code for two
     * payments — the student paid in two months for the same paper. The student's payments page
     * lists QR codes under the payment that produced them, so without this the second month's card
     * would show nothing and look as though that payment gave them no paper.
     *
     * @param slotIds the slots to look up
     * @return one row per (slot, payment) pair
     */
    @Query("SELECT r.paperSlot.id AS slotId, r.paymentSubmission.id AS submissionId "
            + "FROM PaperSlotCreationResult r WHERE r.paperSlot.id IN :slotIds")
    List<SlotSubmissionRef> findSubmissionRefsBySlotIds(@Param("slotIds") List<UUID> slotIds);

    /** Projection for {@link #findSubmissionRefsBySlotIds}. */
    interface SlotSubmissionRef {
        UUID getSlotId();

        UUID getSubmissionId();
    }

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
