package dopaminelite.payment_portal.dto.studypack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO containing study pack information.
 * Returned when retrieving study pack details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPackResponse {
    
    /**
     * Unique identifier of the study pack.
     */
    private UUID id;
    
    /**
     * Name of the study pack.
     */
    private String name;
    
    /**
     * Detailed description of the study pack.
     */
    private String description;
    
    /**
     * Price of the study pack.
     */
    private BigDecimal price;
    
    /**
     * List of class IDs included in this study pack.
     */
    private List<String> classIds;
    
    /**
     * Whether the study pack is currently active.
     */
    private Boolean isActive;
    
    /**
     * Timestamp when the study pack was created.
     */
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when the study pack was last updated.
     */
    private LocalDateTime updatedAt;
    
    /**
     * URL to the thumbnail image for this study pack.
     */
    private String thumbnailUrl;
    
}
