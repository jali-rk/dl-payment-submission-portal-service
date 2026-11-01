package dopaminelite.payment_portal.dto.payhere;

import dopaminelite.payment_portal.dto.submission.StudentSnapshotDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request from BFF to start a PayHere checkout. The student ID comes from BFF's verified JWT.
 * The amount is deliberately not part of the request: it is read from the portal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayHereInitiateRequest {

    @NotNull(message = "Portal ID is required")
    private UUID portalId;

    @NotNull(message = "Student ID is required")
    private UUID studentId;

    /**
     * Captured now because PayHere's notification has no student context, and the submission
     * created on success needs it.
     */
    @NotNull(message = "Student snapshot is required")
    @Valid
    private StudentSnapshotDto studentSnapshot;
}
