package dopaminelite.payment_portal.dto.studypack;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for creating a new study pack.
 * All fields are validated before processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPackCreateRequest {
    
    /**
     * Name of the study pack.
     */
    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;
    
    /**
     * Detailed description of the study pack.
     */
    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;
    
    /**
     * Price of the study pack.
     */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;
    
    /**
     * List of class IDs included in this study pack.
     */
    @NotNull(message = "Class IDs are required")
    @NotEmpty(message = "At least one class ID is required")
    private List<String> classIds;
    
    /**
     * Whether the study pack should be active immediately. Defaults to true.
     */
    private Boolean isActive = true;
    
    /**
     * URL to the thumbnail image for this study pack.
     */
    @Size(max = 500, message = "Thumbnail URL must not exceed 500 characters")
    private String thumbnailUrl;
    
}
