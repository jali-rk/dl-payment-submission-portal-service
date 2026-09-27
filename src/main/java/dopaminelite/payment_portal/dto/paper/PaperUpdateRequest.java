package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for partially updating an existing paper. Only non-null fields are applied.
 *
 * <p>{@code linkedPortalIds} follows PATCH semantics: omitted/null leaves the current links
 * untouched; a present-but-empty list is rejected (a paper must always have at least one
 * linked portal); a present, non-empty list fully replaces the current set.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperUpdateRequest {

    private String title;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * The correlation this paper-event belongs to, or null if it stands alone. When set, the
     * mark scheme, marks and leaderboard belong to the correlation and are shared with its other
     * sitting(s) of the same real paper.
     */
    private UUID correlationId;

    private List<UUID> linkedPortalIds;

}
