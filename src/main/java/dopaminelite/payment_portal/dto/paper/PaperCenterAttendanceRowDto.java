package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of a paper's center-attendance breakdown: either a single physical paper center, or
 * the "grand totals" row. {@code paperCenterId} is null for the totals row and for the
 * "not specified" bucket (students with no center recorded at all); {@code paperCenterName}
 * always has a display value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCenterAttendanceRowDto {

    private String paperCenterId;

    private String paperCenterName;

    private long opened;

    private long attended;

    private long absent;

    /**
     * Percentage (0-100), rounded to one decimal place. Zero when {@code opened} is zero rather
     * than dividing by zero.
     */
    private double attendanceRate;

}
