package dopaminelite.payment_portal.mapper;

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
        response.setPaperId(mark.getPaper().getId());
        response.setStudentId(mark.getStudentId());
        response.setStudent(toStudentDto(mark.getStudentSnapshot()));
        response.setMcqMarks(mark.getMcqMarks());
        response.setStructuredMarks(mark.getStructuredMarks());
        response.setEssayMarks(mark.getEssayMarks());
        response.setTotalMarks(mark.getTotalMarks());
        response.setEnteredByInstructorId(mark.getEnteredByInstructorId());
        response.setLastUpdatedByInstructorId(mark.getLastUpdatedByInstructorId());
        response.setCreatedAt(mark.getCreatedAt());
        response.setUpdatedAt(mark.getUpdatedAt());

        return response;
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
