package dopaminelite.payment_portal.dto.paper;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for renaming a correlation. PATCH semantics: only non-null fields are applied.
 *
 * <p>Both fields are safe to change at any time. Paper-events point at a correlation by id, never
 * by its code, so a rename can't break the grouping or orphan anything — it only changes what
 * people read.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationUpdateRequest {

    @Size(max = 64, message = "Code must not exceed 64 characters")
    private String code;

    @Size(max = 255, message = "Display name must not exceed 255 characters")
    private String displayName;

}
