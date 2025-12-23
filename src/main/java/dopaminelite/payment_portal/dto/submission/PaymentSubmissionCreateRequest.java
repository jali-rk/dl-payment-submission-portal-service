package dopaminelite.payment_portal.dto.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a new payment submission.
 * Students use this to submit payment proof to a portal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSubmissionCreateRequest {
    
    /**
     * UUID of the student making the submission.
     */
    @NotNull(message = "Student ID is required")
    private UUID studentId;
    
    /**
     * Portal name confirmation to prevent accidental submissions to wrong portals.
     */
    @NotBlank(message = "Portal name confirmation is required")
    private String portalNameConfirmation;
    
    /**
     * List of uploaded files. At least one file is required.
     */
    @NotEmpty(message = "At least one file is required")
    @Valid
    private List<UploadedFileRefDto> files;
    
}
