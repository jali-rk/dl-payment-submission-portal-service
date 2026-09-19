package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO containing a single mark record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperMarkResponse {

    private UUID id;

    /**
     * The paper-event this mark is on, or null when it belongs to a correlation instead — i.e.
     * when the paper was sat across more than one paper-event and the mark is on the paper as a
     * whole rather than on either sitting of it. Exactly one of this and {@link #correlationId}
     * is ever set.
     */
    private UUID paperId;

    /**
     * The correlation this mark belongs to, or null for a mark on a standalone paper-event.
     */
    private UUID correlationId;

    /**
     * What to call this paper: the correlation's display name when there is one, otherwise the
     * paper-event's own title. Saves every caller having to resolve that themselves, and keeps
     * a correlated paper from being shown under two different names.
     */
    private String paperTitle;

    private UUID studentId;

    private MarkStudentSnapshotDto student;

    private BigDecimal mcqMarks;

    private BigDecimal structuredMarks;

    private BigDecimal essayMarks;

    private BigDecimal totalMarks;

    private Integer rank;

    private UUID enteredByInstructorId;

    private UUID lastUpdatedByInstructorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
