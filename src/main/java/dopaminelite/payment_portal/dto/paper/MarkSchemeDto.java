package dopaminelite.payment_portal.dto.paper;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A paper's mark scheme: which of the three fixed sections (MCQ, Structured, Essay) are
 * enabled, and each enabled section's maximum mark. Each field is independent — 0 to 3 may be
 * set, and they carry no required relationship to each other (they do not need to sum to 100
 * or anything else). At least one must be set for the scheme to be meaningful, and once any
 * mark exists for the paper the scheme becomes immutable — both enforced imperatively in
 * {@code PaperService}, not via annotations here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkSchemeDto {

    @DecimalMin(value = "0.0", message = "MCQ max marks must not be negative")
    @DecimalMax(value = "100.0", message = "MCQ max marks must not exceed 100")
    @Digits(integer = 3, fraction = 3, message = "MCQ max marks supports up to 3 decimal places")
    private BigDecimal mcqMaxMarks;

    @DecimalMin(value = "0.0", message = "Structured max marks must not be negative")
    @DecimalMax(value = "100.0", message = "Structured max marks must not exceed 100")
    @Digits(integer = 3, fraction = 3, message = "Structured max marks supports up to 3 decimal places")
    private BigDecimal structuredMaxMarks;

    @DecimalMin(value = "0.0", message = "Essay max marks must not be negative")
    @DecimalMax(value = "100.0", message = "Essay max marks must not exceed 100")
    @Digits(integer = 3, fraction = 3, message = "Essay max marks supports up to 3 decimal places")
    private BigDecimal essayMaxMarks;

}
