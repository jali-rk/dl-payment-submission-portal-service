package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.submission.PaymentSubmissionResponse;
import dopaminelite.payment_portal.dto.submission.UploadedFileRefDto;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.UploadedFile;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Mapper for converting PaymentSubmission entities to response DTOs.
 */
@Component
public class PaymentSubmissionMapper {
    
    /**
     * Converts a PaymentSubmission entity to a PaymentSubmissionResponse DTO.
     *
     * @param submission the entity to convert, can be null
     * @return the response DTO, or null if the input is null
     */
    public PaymentSubmissionResponse toResponse(PaymentSubmission submission) {
        if (submission == null) {
            return null;
        }
        
        PaymentSubmissionResponse response = new PaymentSubmissionResponse();
        response.setId(submission.getId());
        response.setStudentId(submission.getStudentId());
        response.setPortalId(submission.getPortal().getId());
        response.setStatus(submission.getStatus());
        response.setRejectionReason(submission.getRejectionReason());
        response.setPortalNameAtSubmission(submission.getPortalNameAtSubmission());
        response.setSubmittedAt(submission.getSubmittedAt());
        response.setLastUpdatedAt(submission.getLastUpdatedAt());
        
        if (submission.getUploadedFiles() != null) {
            response.setUploadedFiles(
                submission.getUploadedFiles().stream()
                    .map(this::toFileRefDto)
                    .collect(Collectors.toList())
            );
        }
        
        return response;
    }
    
    /**
     * Converts an UploadedFile entity to an UploadedFileRefDto.
     *
     * @param file the entity to convert
     * @return the DTO containing file metadata
     */
    private UploadedFileRefDto toFileRefDto(UploadedFile file) {
        UploadedFileRefDto dto = new UploadedFileRefDto();
        dto.setFileId(file.getFileId());
        dto.setFileName(file.getFileName());
        dto.setFileType(file.getFileType());
        return dto;
    }
    
}
