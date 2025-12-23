package dopaminelite.payment_portal.dto.submission;

import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO containing payment submission information.
 * Returned when retrieving submission details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSubmissionResponse {
    
    /**
     * Unique identifier of the submission.
     */
    private UUID id;
    
    /**
     * UUID of the student who made the submission.
     */
    private UUID studentId;
    
    /**
     * UUID of the portal this submission belongs to.
     */
    private UUID portalId;
    
    /**
     * Current approval status of the submission.
     */
    private SubmissionStatus status;
    
    /**
     * Reason provided when the submission was rejected.
     */
    private String rejectionReason;
    
    /**
     * List of files uploaded with this submission.
     */
    private List<UploadedFileRefDto> uploadedFiles;
    
    /**
     * Snapshot of the portal name at submission time.
     */
    private String portalNameAtSubmission;
    
    /**
     * Timestamp when the submission was created.
     */
    private LocalDateTime submittedAt;
    
    /**
     * Timestamp when the submission was last updated.
     */
    private LocalDateTime lastUpdatedAt;
    
}
