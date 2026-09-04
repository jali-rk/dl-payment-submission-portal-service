package dopaminelite.payment_portal.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A student's public data as returned by the BFF's student-by-code-number lookup
 * ({@code GET /students/by-code-number?codeNumber=}). The HTTP response body wraps this in a
 * {@link BffObjectResponse} envelope ({@code {"success": ..., "data": {...this shape...}}}) —
 * deserialize the envelope and read {@code getData()}, not this class directly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentLookupDto {

    private UUID id;

    private String fullName;

    private String email;

    private String whatsappNumber;

    private String codeNumber;

}
