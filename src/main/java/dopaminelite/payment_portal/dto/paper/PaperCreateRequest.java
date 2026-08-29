package dopaminelite.payment_portal.dto.paper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a new paper.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    /**
     * IDs of the payment portals that fund this paper. Required and non-empty — a paper
     * with no linked portal can never generate a slot.
     */
    @NotEmpty(message = "At least one linked payment portal is required")
    private List<UUID> linkedPortalIds;

}
