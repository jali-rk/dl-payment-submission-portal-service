package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.paper.CorrelationRefDto;
import dopaminelite.payment_portal.dto.paper.MarkSchemeDto;
import dopaminelite.payment_portal.dto.paper.PaperResponse;
import dopaminelite.payment_portal.dto.submission.PortalRefDto;
import dopaminelite.payment_portal.entity.MarkOwner;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperCorrelation;
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
        // Grading belongs to the correlation when this paper-event is part of one, so the scheme
        // and leaderboard state are read from there rather than from this row's own (unused)
        // columns - otherwise an admin opening one sitting of a correlated paper would see an
        // empty mark scheme and an unpublished leaderboard while the other sitting shows the real
        // ones.
        MarkOwner owner = paper.getCorrelation() != null ? paper.getCorrelation() : paper;
        response.setMarkScheme(new MarkSchemeDto(
                owner.getMcqMaxMarks(), owner.getStructuredMaxMarks(), owner.getEssayMaxMarks()));
        response.setLeaderboardPublished(owner.isLeaderboardPublished());
        response.setLeaderboardLastGeneratedAt(owner.getLeaderboardLastGeneratedAt());
        response.setCorrelation(toCorrelationRef(paper.getCorrelation()));

        List<PortalRefDto> linkedPortals = paper.getLinkedPortals().stream()
                .map(this::toPortalRef)
                .collect(Collectors.toList());
        response.setLinkedPortals(linkedPortals);

        return response;
    }

    private PortalRefDto toPortalRef(PaymentPortal portal) {
        return new PortalRefDto(portal.getId(), portal.getDisplayName());
    }

    private CorrelationRefDto toCorrelationRef(PaperCorrelation correlation) {
        return correlation == null
                ? null
                : new CorrelationRefDto(correlation.getId(), correlation.getCode(), correlation.getDisplayName());
    }

}
