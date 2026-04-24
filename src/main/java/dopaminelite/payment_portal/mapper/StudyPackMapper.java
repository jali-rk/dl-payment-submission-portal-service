package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.studypack.StudyPackResponse;
import dopaminelite.payment_portal.entity.StudyPack;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting StudyPack entities to response DTOs.
 */
@Component
public class StudyPackMapper {
    
    /**
     * Converts a StudyPack entity to a StudyPackResponse DTO.
     *
     * @param studyPack the entity to convert, can be null
     * @return the response DTO, or null if the input is null
     */
    public StudyPackResponse toResponse(StudyPack studyPack) {
        if (studyPack == null) {
            return null;
        }
        
        StudyPackResponse response = new StudyPackResponse();
        response.setId(studyPack.getId());
        response.setName(studyPack.getName());
        response.setDescription(studyPack.getDescription());
        response.setPrice(studyPack.getPrice());
        response.setClassIds(studyPack.getClassIds());
        response.setIsActive(studyPack.getIsActive());
        response.setThumbnailUrl(studyPack.getThumbnailUrl());
        response.setCreatedAt(studyPack.getCreatedAt());
        response.setUpdatedAt(studyPack.getUpdatedAt());
        
        return response;
    }
    
}
