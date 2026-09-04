package dopaminelite.payment_portal.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic envelope for a BFF endpoint that returns a single object, e.g.
 * {@code {"success": true, "data": {...}}}. This is the BFF's standard response shape
 * (see its {@code responseMapper.successResponse}) for any non-list endpoint — distinct from
 * {@link BffStandardResponse}, which wraps a {@code {items, total}} list specifically.
 *
 * @param <T> the type of the wrapped object
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BffObjectResponse<T> {

    private boolean success;

    private T data;

}
