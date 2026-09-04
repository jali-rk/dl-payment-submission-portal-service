package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a paper's leaderboard: its current publish state, when it was last
 * (re)generated and by whom, and a page of the ranked entries as of that last generation.
 * {@code entries} only ever reflects marks that existed at the time of the last "Generate
 * Ranks" run — marks added, edited, or deleted since then are not reflected until the next
 * generation.
 *
 * <p>{@code entries} is paginated ({@code total} carries the full ranked count, independent of
 * how many entries are on this page) since a paper can realistically have thousands of ranked
 * students. {@code callerEntry} is the calling student's own ranked row, always populated
 * regardless of which page was requested (null if the caller isn't a ranked student on this
 * paper) — so a caller never has to page through the whole board to find their own rank.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperLeaderboardResponse {

    private UUID paperId;

    private String paperTitle;

    private MarkSchemeDto markScheme;

    private boolean published;

    private LocalDateTime lastGeneratedAt;

    private UUID lastGeneratedByInstructorId;

    private List<LeaderboardEntryDto> entries;

    private long total;

    private LeaderboardEntryDto callerEntry;

}
