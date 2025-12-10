package dopaminelite.payment_portal.dto.submission;

import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSubmissionResponse {
    
    private UUID id;
    private UUID studentId;
    private UUID portalId;
    private SubmissionStatus status;
    private String rejectionReason;
    private List<UploadedFileRefDto> uploadedFiles;
    private String portalNameAtSubmission;
    private LocalDateTime submittedAt;
    private LocalDateTime lastUpdatedAt;
    
}
