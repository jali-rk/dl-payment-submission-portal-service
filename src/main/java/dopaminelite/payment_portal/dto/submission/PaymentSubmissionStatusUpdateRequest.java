package dopaminelite.payment_portal.dto.submission;

import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating the status of a payment submission.
 * Used by administrators to approve or reject submissions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSubmissionStatusUpdateRequest {
    
    /**
     * New status to set (PENDING, APPROVED, or REJECTED).
     */
    @NotNull(message = "Status is required")
    private SubmissionStatus status;
    
    /**
     * Reason for rejection. Required when status is REJECTED.
     */
    private String rejectionReason;
    
}
