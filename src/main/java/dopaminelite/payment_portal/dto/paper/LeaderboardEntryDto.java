package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single ranked row on a paper's leaderboard. Deliberately exposes only the student's name
 * and code number (no email/whatsapp) — matches what the instructor-facing marks table already
 * surfaces, so this carries no additional PII regardless of viewer role.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDto {

    private int rank;

    private UUID studentId;

    private String fullName;

    private String codeNumber;

    private BigDecimal mcqMarks;

    private BigDecimal structuredMarks;

    private BigDecimal essayMarks;

    private BigDecimal totalMarks;

}
