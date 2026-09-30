package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.paper.LeaderboardEntryDto;
import dopaminelite.payment_portal.dto.paper.MarkStudentSnapshotDto;
import dopaminelite.payment_portal.dto.paper.PaperMarkResponse;
import dopaminelite.payment_portal.entity.MarkStudentSnapshot;
import dopaminelite.payment_portal.entity.PaperMark;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting PaperMark entities to response DTOs.
 */
@Component
public class PaperMarkMapper {

    /**
     * Converts a PaperMark entity to a PaperMarkResponse DTO.
     *
     * @param mark the entity to convert, can be null
     * @return the response DTO, or null if the input is null
     */
    public PaperMarkResponse toResponse(PaperMark mark) {
        if (mark == null) {
            return null;
        }

        PaperMarkResponse response = new PaperMarkResponse();
        response.setId(mark.getId());
        // Exactly one of these is set - a mark belongs either to a standalone paper-event or to
        // the correlation grouping several sittings of one paper. paperId is deliberately left
        // null in the latter case rather than filled with an arbitrary member's id, which would
        // read as "this mark is on that sitting" and isn't true of either of them.
        response.setPaperId(mark.getPaper() != null ? mark.getPaper().getId() : null);
        response.setCorrelationId(mark.getCorrelation() != null ? mark.getCorrelation().getId() : null);
        response.setPaperTitle(mark.getCorrelation() != null
                ? mark.getCorrelation().getDisplayName()
                : mark.getPaper().getTitle());
        response.setStudentId(mark.getStudentId());
        response.setStudent(toStudentDto(mark.getStudentSnapshot()));
        response.setMcqMarks(mark.getMcqMarks());
        response.setStructuredMarks(mark.getStructuredMarks());
        response.setEssayMarks(mark.getEssayMarks());
        response.setTotalMarks(mark.getTotalMarks());
        response.setRank(mark.getRank());
        response.setEnteredByInstructorId(mark.getEnteredByInstructorId());
        response.setLastUpdatedByInstructorId(mark.getLastUpdatedByInstructorId());
        response.setCreatedAt(mark.getCreatedAt());
        response.setUpdatedAt(mark.getUpdatedAt());

        return response;
    }

    /**
     * Converts a ranked PaperMark entity to a leaderboard entry. Only meaningful for marks that
     * already have a {@code rank} assigned (i.e. included in the paper's most recent "Generate
     * Ranks" run) — callers are expected to have already filtered to those.
     *
     * @param mark the ranked entity to convert
     * @return the leaderboard entry DTO
     */
    public LeaderboardEntryDto toLeaderboardEntry(PaperMark mark) {
        LeaderboardEntryDto entry = new LeaderboardEntryDto();
        entry.setRank(mark.getRank());
        entry.setStudentId(mark.getStudentId());
        MarkStudentSnapshot snapshot = mark.getStudentSnapshot();
        if (snapshot != null) {
            entry.setFullName(snapshot.getFullName());
            entry.setCodeNumber(snapshot.getCodeNumber());
        }
        entry.setMcqMarks(mark.getMcqMarks());
        entry.setStructuredMarks(mark.getStructuredMarks());
        entry.setEssayMarks(mark.getEssayMarks());
        entry.setTotalMarks(mark.getTotalMarks());
        return entry;
    }

    private MarkStudentSnapshotDto toStudentDto(MarkStudentSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        MarkStudentSnapshotDto dto = new MarkStudentSnapshotDto();
        dto.setCodeNumber(snapshot.getCodeNumber());
        dto.setFullName(snapshot.getFullName());
        dto.setEmail(snapshot.getEmail());
        dto.setWhatsappNumber(snapshot.getWhatsappNumber());
        return dto;
    }

}
