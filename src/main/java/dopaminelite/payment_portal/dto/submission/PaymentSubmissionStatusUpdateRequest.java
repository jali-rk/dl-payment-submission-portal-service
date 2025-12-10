package dopaminelite.payment_portal.dto.submission;

import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSubmissionStatusUpdateRequest {
    
    @NotNull(message = "Status is required")
    private SubmissionStatus status;
    
    private String rejectionReason;
    
}
