package dopaminelite.payment_portal.dto.paper;

import dopaminelite.payment_portal.dto.submission.PortalRefDto;
import dopaminelite.payment_portal.dto.submission.StudentSnapshotDto;
import dopaminelite.payment_portal.entity.enums.PaperSlotStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO containing a paper slot's full details: identity, computed status, and the
 * student/payment details pulled from its linked submission.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperSlotResponse {

    private UUID id;

    private UUID qrToken;

    private PaperSlotStatus status;

    private UUID paperId;

    private String paperTitle;

    private UUID submissionId;

    private UUID studentId;

    private StudentSnapshotDto student;

    private PortalRefDto portal;

    private LocalDateTime consumedAt;

    private UUID consumedByInstructorId;

    private LocalDateTime createdAt;

}
