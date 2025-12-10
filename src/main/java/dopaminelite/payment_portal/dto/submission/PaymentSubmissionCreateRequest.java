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

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSubmissionCreateRequest {
    
    @NotNull(message = "Student ID is required")
    private UUID studentId;
    
    @NotBlank(message = "Portal name confirmation is required")
    private String portalNameConfirmation;
    
    @NotEmpty(message = "At least one file is required")
    @Valid
    private List<UploadedFileRefDto> files;
    
}
