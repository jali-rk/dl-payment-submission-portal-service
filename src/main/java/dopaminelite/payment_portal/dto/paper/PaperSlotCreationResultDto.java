package dopaminelite.payment_portal.dto.paper;

import dopaminelite.payment_portal.entity.enums.PaperSlotCreationOutcome;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO describing the outcome of one paper's slot-creation attempt for a submission.
 * {@code slot} is populated when a slot exists (outcome CREATED or ALREADY_EXISTS) and null
 * when outcome is FAILED; {@code message} is populated only on FAILED.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperSlotCreationResultDto {

    private UUID paperId;

    private String paperTitle;

    private PaperSlotCreationOutcome outcome;

    private String message;

    private PaperSlotResponse slot;

    private LocalDateTime attemptedAt;

}
