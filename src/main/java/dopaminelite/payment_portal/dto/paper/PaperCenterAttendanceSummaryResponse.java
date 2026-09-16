package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a paper's attendance breakdown by physical paper center: one row per active
 * center (including centers with zero slots for this paper), plus a grand-totals row across all
 * of them. {@code centers} is not paginated - the number of paper centers is always small.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCenterAttendanceSummaryResponse {

    private UUID paperId;

    private String paperTitle;

    private PaperCenterAttendanceRowDto totals;

    private List<PaperCenterAttendanceRowDto> centers;

}
