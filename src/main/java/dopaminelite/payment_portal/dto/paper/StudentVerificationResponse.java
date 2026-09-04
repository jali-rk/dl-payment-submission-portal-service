package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for the live "verify this student exists" lookup an instructor's UI calls as a
 * code number is typed, before submitting the full marks form.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentVerificationResponse {

    private UUID studentId;

    private String codeNumber;

    private String fullName;

    private String email;

    private String whatsappNumber;

    /**
     * Whether this student already has a mark recorded on this paper.
     */
    private boolean alreadyHasMark;

}
