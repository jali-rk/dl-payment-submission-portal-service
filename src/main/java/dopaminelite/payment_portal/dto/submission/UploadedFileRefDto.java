package dopaminelite.payment_portal.dto.submission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadedFileRefDto {
    
    private UUID fileId;
    private String fileName;
    private String fileType;
    
}
