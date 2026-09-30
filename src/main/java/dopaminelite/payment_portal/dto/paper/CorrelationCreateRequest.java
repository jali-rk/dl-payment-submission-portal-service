package dopaminelite.payment_portal.dto.paper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a correlation — the grouping that says two paper-events are the same
 * real paper.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationCreateRequest {

    /**
     * Admin-facing identifier, e.g. {@code OCT-W1}. Uppercased on save; letters, digits, hyphens
     * and underscores only, so it can't fail to match through a stray case or space difference.
     */
    @NotBlank(message = "Code is required")
    @Size(max = 64, message = "Code must not exceed 64 characters")
    private String code;

    /**
     * What instructors and students see wherever this paper is named, e.g.
     * {@code October Week 1 Paper}.
     */
    @NotBlank(message = "Display name is required")
    @Size(max = 255, message = "Display name must not exceed 255 characters")
    private String displayName;

}
