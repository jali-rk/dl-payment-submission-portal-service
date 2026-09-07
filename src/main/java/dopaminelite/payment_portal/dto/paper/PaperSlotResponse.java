package dopaminelite.payment_portal.dto.paper;

import dopaminelite.payment_portal.dto.submission.PortalRefDto;
import dopaminelite.payment_portal.dto.submission.StudentSnapshotDto;
import dopaminelite.payment_portal.entity.enums.PaperSlotStatus;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
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

    /**
     * The linked submission's approval status. Slots only exist for approved submissions
     * (see PaperSlotService), so this is always APPROVED in practice - included so the
     * instructor's scan screen can show it explicitly rather than the instructor having to
     * take that on faith.
     */
    private SubmissionStatus paymentStatus;

    private UUID studentId;

    private StudentSnapshotDto student;

    private PortalRefDto portal;

    private LocalDateTime consumedAt;

    private UUID consumedByInstructorId;

    private LocalDateTime createdAt;

}
