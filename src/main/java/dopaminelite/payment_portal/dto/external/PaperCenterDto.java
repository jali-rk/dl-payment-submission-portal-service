package dopaminelite.payment_portal.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a paper center from the BFF service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperCenterDto {

    /**
     * Unique identifier of the paper center.
     */
    private String id;

    /**
     * Name of the paper center.
     */
    private String name;

    /**
     * Timestamp when the paper center was created.
     */
    private String createdAt;

    /**
     * Timestamp when the paper center was last updated.
     */
    private String updatedAt;
}
