package dopaminelite.payment_portal.dto.paper;

import dopaminelite.payment_portal.dto.submission.PortalRefDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO containing paper information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperResponse {

    private UUID id;

    private String title;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private List<PortalRefDto> linkedPortals;

    private UUID createdByAdminId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
