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

    private UUID paperId;

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
