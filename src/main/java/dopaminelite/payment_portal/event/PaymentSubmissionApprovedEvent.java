package dopaminelite.payment_portal.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Published when a {@code PaymentSubmission} transitions to APPROVED. Carries only the
 * submission ID — listeners re-fetch fresh state rather than relying on a possibly-stale
 * entity reference from the publisher's (already-closing) persistence context.
 */
@Getter
@AllArgsConstructor
public class PaymentSubmissionApprovedEvent {

    private final UUID submissionId;

}
