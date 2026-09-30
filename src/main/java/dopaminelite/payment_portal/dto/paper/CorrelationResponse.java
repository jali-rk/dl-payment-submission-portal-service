package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full view of a correlation, including the grading it owns on behalf of its paper-events and
 * which paper-events those are.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationResponse {

    private UUID id;

    private String code;

    private String displayName;

    /**
     * Shared by every paper-event in this correlation — the sittings of one paper cannot be
     * marked out of different totals.
     */
    private MarkSchemeDto markScheme;

    private boolean leaderboardPublished;

    private LocalDateTime leaderboardLastGeneratedAt;

    /**
     * The paper-events grouped by this correlation, so an admin can see at a glance which
     * sittings it covers before renaming or deleting it.
     */
    private List<PaperRefDto> paperEvents;

}
