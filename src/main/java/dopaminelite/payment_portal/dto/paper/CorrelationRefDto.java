package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Lightweight reference to the correlation a paper-event belongs to, embedded in responses that
 * describe the paper-event itself.
 *
 * <p>Carries both names on purpose: admins recognise the correlation by its {@code code}, while
 * {@code displayName} is what instructors and students are shown in place of the paper-event's own
 * title. Null on a paper-event that stands alone.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationRefDto {

    private UUID id;

    private String code;

    private String displayName;

}
