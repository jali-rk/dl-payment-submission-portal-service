package dopaminelite.payment_portal.event;

import dopaminelite.payment_portal.service.PaperSlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to a payment submission being approved by creating paper slots for every paper
 * linked to the submission's portal.
 *
 * <p>Bound to {@code AFTER_COMMIT} deliberately: this guarantees the approval's own
 * transaction has already committed by the time this runs, so a fresh re-fetch of the
 * submission is guaranteed to see the new APPROVED status (see the transactional-correctness
 * notes in the implementation plan). Spring invokes {@code AFTER_COMMIT} listeners
 * synchronously, on the same thread, as part of the commit — not on a background thread — so
 * this still completes before the approval's HTTP response is sent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSubmissionApprovedEventListener {

    private final PaperSlotService paperSlotService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionApproved(PaymentSubmissionApprovedEvent event) {
        try {
            paperSlotService.createSlotsForApprovedSubmission(event.getSubmissionId());
        } catch (Exception e) {
            log.error("Failed to process paper slot creation for approved submission {}: {}",
                    event.getSubmissionId(), e.getMessage(), e);
        }
    }

}
