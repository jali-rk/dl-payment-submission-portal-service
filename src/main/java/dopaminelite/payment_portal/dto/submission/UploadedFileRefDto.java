package dopaminelite.payment_portal.dto.submission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO representing metadata for an uploaded file.
 * Used when creating submissions or returning file information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadedFileRefDto {
    
    /**
     * External file ID reference (e.g., from a file storage service).
     */
    private UUID fileId;
    
    /**
     * Original filename of the uploaded file.
     */
    private String fileName;
    
    /**
     * MIME type or file extension.
     */
    private String fileType;
    
}
