package dopaminelite.payment_portal.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic response wrapper for paginated list results.
 * Contains the list of items and total count for pagination support.
 *
 * @param <T> the type of items in the paginated response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {
    
    /**
     * List of items for the current page.
     */
    private List<T> items;
    
    /**
     * Total count of items across all pages.
     */
    private Long total;
    
}
