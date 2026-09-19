package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight reference to a paper-event: enough to identify which sitting it is without pulling
 * in its portals or grading.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperRefDto {

    private UUID id;

    private String title;

    private LocalDate startDate;

    private LocalDate endDate;

}
