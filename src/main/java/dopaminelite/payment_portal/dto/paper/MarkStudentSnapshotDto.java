package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only reflection of the student snapshot captured on a {@code PaperMark} at entry time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkStudentSnapshotDto {

    private String codeNumber;

    private String fullName;

    private String email;

    private String whatsappNumber;

}
