package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One student row in the drill-down list behind a paper-center-attendance summary row.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCenterAttendanceStudentDto {

    private UUID studentId;

    private String codeNumber;

    private String fullName;

    /**
     * Resolved center id: {@link dopaminelite.payment_portal.service.PaperAttendanceService#UNASSIGNED_CENTER_KEY}
     * if the student has no center recorded, otherwise the raw center key (matches a summary
     * row's {@code paperCenterId}).
     */
    private String paperCenterId;

    private String paperCenterName;

    private boolean attended;

    private LocalDateTime consumedAt;

}
