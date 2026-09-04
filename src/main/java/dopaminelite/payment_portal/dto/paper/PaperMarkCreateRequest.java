package dopaminelite.payment_portal.dto.paper;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for recording a new mark against a paper.
 *
 * <p>{@code mcqMarks}/{@code structuredMarks}/{@code essayMarks} are required exactly when the
 * paper's corresponding section is enabled, and must not exceed that section's configured
 * max — both checked imperatively in {@code PaperMarkService} against the specific paper, since
 * the bound depends on paper-specific configuration and can't be expressed as a static
 * annotation here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperMarkCreateRequest {

    @NotBlank(message = "Student code number is required")
    private String studentCodeNumber;

    @DecimalMin(value = "0.0", message = "MCQ marks must not be negative")
    @Digits(integer = 3, fraction = 3, message = "MCQ marks supports up to 3 decimal places")
    private BigDecimal mcqMarks;

    @DecimalMin(value = "0.0", message = "Structured marks must not be negative")
    @Digits(integer = 3, fraction = 3, message = "Structured marks supports up to 3 decimal places")
    private BigDecimal structuredMarks;

    @DecimalMin(value = "0.0", message = "Essay marks must not be negative")
    @Digits(integer = 3, fraction = 3, message = "Essay marks supports up to 3 decimal places")
    private BigDecimal essayMarks;

    @NotNull(message = "Total marks is required")
    @DecimalMin(value = "0.0", message = "Total marks must not be negative")
    @DecimalMax(value = "100.0", message = "Total marks must not exceed 100")
    @Digits(integer = 3, fraction = 3, message = "Total marks supports up to 3 decimal places")
    private BigDecimal totalMarks;

}
