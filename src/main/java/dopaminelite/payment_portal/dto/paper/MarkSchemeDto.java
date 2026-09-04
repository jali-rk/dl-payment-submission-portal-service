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
 *
 * <p>The 100000 ceiling below isn't a real, intended limit on how a paper can be scored — an
 * admin should be free to set a section's max to whatever the paper actually needs (1000,
 * 10000, ...). It's a sanity backstop against a stray extra digit, set high enough that it
 * should never actually bind, which is also why the messages don't quote the number: it's
 * not meant to read as an advertised rule.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkSchemeDto {

    private static final String MAX_MARKS_CEILING = "100000.0";
    private static final int MAX_MARKS_INTEGER_DIGITS = 6;

    @DecimalMin(value = "0.0", message = "MCQ max marks must not be negative")
    @DecimalMax(value = MAX_MARKS_CEILING, message = "MCQ max marks is too large")
    @Digits(integer = MAX_MARKS_INTEGER_DIGITS, fraction = 3, message = "MCQ max marks supports up to 3 decimal places")
    private BigDecimal mcqMaxMarks;

    @DecimalMin(value = "0.0", message = "Structured max marks must not be negative")
    @DecimalMax(value = MAX_MARKS_CEILING, message = "Structured max marks is too large")
    @Digits(integer = MAX_MARKS_INTEGER_DIGITS, fraction = 3, message = "Structured max marks supports up to 3 decimal places")
    private BigDecimal structuredMaxMarks;

    @DecimalMin(value = "0.0", message = "Essay max marks must not be negative")
    @DecimalMax(value = MAX_MARKS_CEILING, message = "Essay max marks is too large")
    @Digits(integer = MAX_MARKS_INTEGER_DIGITS, fraction = 3, message = "Essay max marks supports up to 3 decimal places")
    private BigDecimal essayMaxMarks;

}
