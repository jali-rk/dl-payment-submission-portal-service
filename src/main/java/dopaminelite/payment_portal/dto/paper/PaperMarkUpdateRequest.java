package dopaminelite.payment_portal.dto.paper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for partially updating an existing mark. Only non-null fields are applied — the
 * student a mark belongs to is never editable through this endpoint (delete and re-create
 * instead). Bounds against the paper's mark scheme are re-checked imperatively in
 * {@code PaperMarkService}, same as {@link PaperMarkCreateRequest}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperMarkUpdateRequest {

    private BigDecimal mcqMarks;

    private BigDecimal structuredMarks;

    private BigDecimal essayMarks;

    private BigDecimal totalMarks;

}
