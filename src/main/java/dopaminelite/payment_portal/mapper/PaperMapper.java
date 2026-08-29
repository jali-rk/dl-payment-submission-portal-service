package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.paper.PaperResponse;
import dopaminelite.payment_portal.dto.submission.PortalRefDto;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaymentPortal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting Paper entities to response DTOs.
 */
@Component
public class PaperMapper {

    /**
     * Converts a Paper entity to a PaperResponse DTO.
     *
     * @param paper the entity to convert, can be null
     * @return the response DTO, or null if the input is null
     */
    public PaperResponse toResponse(Paper paper) {
        if (paper == null) {
            return null;
        }

        PaperResponse response = new PaperResponse();
        response.setId(paper.getId());
        response.setTitle(paper.getTitle());
        response.setDescription(paper.getDescription());
        response.setStartDate(paper.getStartDate());
        response.setEndDate(paper.getEndDate());
        response.setCreatedByAdminId(paper.getCreatedByAdminId());
        response.setCreatedAt(paper.getCreatedAt());
        response.setUpdatedAt(paper.getUpdatedAt());

        List<PortalRefDto> linkedPortals = paper.getLinkedPortals().stream()
                .map(this::toPortalRef)
                .collect(Collectors.toList());
        response.setLinkedPortals(linkedPortals);

        return response;
    }

    private PortalRefDto toPortalRef(PaymentPortal portal) {
        return new PortalRefDto(portal.getId(), portal.getDisplayName());
    }

}
