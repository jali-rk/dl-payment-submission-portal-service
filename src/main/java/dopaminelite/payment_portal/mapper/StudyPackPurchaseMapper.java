package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.studypack.StudyPackPurchaseResponse;
import dopaminelite.payment_portal.dto.studypack.StudyPackResponse;
import dopaminelite.payment_portal.entity.StudyPack;
import dopaminelite.payment_portal.entity.StudyPackPurchase;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting StudyPackPurchase entities to response DTOs.
 */
@Component
public class StudyPackPurchaseMapper {
    
    private final StudyPackMapper studyPackMapper;
    
    public StudyPackPurchaseMapper(StudyPackMapper studyPackMapper) {
        this.studyPackMapper = studyPackMapper;
    }
    
    /**
     * Converts a StudyPackPurchase entity to a StudyPackPurchaseResponse DTO.
     *
     * @param purchase the entity to convert, can be null
     * @return the response DTO, or null if the input is null
     */
    public StudyPackPurchaseResponse toResponse(StudyPackPurchase purchase) {
        if (purchase == null) {
            return null;
        }
        
        StudyPackPurchaseResponse response = new StudyPackPurchaseResponse();
        response.setId(purchase.getId());
        response.setStudentId(purchase.getStudentId());
        response.setAmount(purchase.getAmount());
        response.setCurrency(purchase.getCurrency());
        response.setPurchaseStatus(purchase.getPurchaseStatus());
        response.setPaymentGateway(purchase.getPaymentGateway());
        response.setTransactionId(purchase.getTransactionId());
        response.setEnrollmentCompleted(purchase.getEnrollmentCompleted());
        response.setEnrollmentError(purchase.getEnrollmentError());
        response.setPurchasedAt(purchase.getCreatedAt());
        response.setUpdatedAt(purchase.getUpdatedAt());
        
        // Map study pack details
        if (purchase.getStudyPack() != null) {
            StudyPackResponse studyPackResponse = studyPackMapper.toResponse(purchase.getStudyPack());
            response.setStudyPack(studyPackResponse);
        }
        
        return response;
    }
    
}
