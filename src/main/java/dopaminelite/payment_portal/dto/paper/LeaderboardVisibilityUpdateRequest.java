package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for a MAIN_ADMIN publishing or hiding a paper's leaderboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardVisibilityUpdateRequest {

    private boolean published;

}
