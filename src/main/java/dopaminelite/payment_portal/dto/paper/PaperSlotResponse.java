package dopaminelite.payment_portal.dto.paper;

import dopaminelite.payment_portal.dto.submission.PortalRefDto;
import dopaminelite.payment_portal.dto.submission.StudentSnapshotDto;
import dopaminelite.payment_portal.entity.enums.PaperSlotStatus;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    /**
     * The last day this slot can be scanned. Usually the paper-event's end date, but later when
     * a subsequent payment extended the window for the same paper rather than issuing a second
     * QR code — so this, not the paper's own end date, is what the holder can rely on.
     */
    private LocalDate validUntil;

    /**
     * Every payment this one QR code covers, which is usually just {@link #submissionId} but is
     * two when a later payment extended this slot rather than issuing a second code. The student's
     * payments page lists each QR under the payment it belongs to, so it needs to show this one
     * under both - otherwise the second month's card looks as though it produced nothing.
     */
    private List<UUID> coveredSubmissionIds;


    private SubmissionStatus paymentStatus;

    private UUID studentId;

    private StudentSnapshotDto student;

    private PortalRefDto portal;

    private LocalDateTime consumedAt;

    private UUID consumedByInstructorId;

    private LocalDateTime createdAt;

}
