package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.submission.PaymentSubmissionResponse;
import dopaminelite.payment_portal.dto.submission.PortalRefDto;
import dopaminelite.payment_portal.dto.submission.StudentSnapshotDto;
import dopaminelite.payment_portal.dto.submission.UploadedFileRefDto;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.StudentSnapshot;
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
        response.setPortalId(submission.getPortal() != null ? submission.getPortal().getId() : null);
        response.setStatus(submission.getStatus());
        response.setRejectionReason(submission.getRejectionReason());
        response.setPortalNameAtSubmission(submission.getPortalNameAtSubmission());
        response.setPortal(submission.getPortal() != null ? 
            new PortalRefDto(submission.getPortal().getId(), submission.getPortal().getDisplayName()) : null);
        response.setStudent(toStudentSnapshotDto(submission.getStudentSnapshot()));
        response.setSubmittedAt(submission.getSubmittedAt());
        response.setLastUpdatedAt(submission.getLastUpdatedAt());
        response.setSubmissionType(submission.getSubmissionType());
        response.setStudyPackId(submission.getStudyPack() != null ? submission.getStudyPack().getId() : null);

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

    /**
     * Converts a StudentSnapshot entity to a StudentSnapshotDto.
     *
     * @param snapshot the entity to convert, can be null
     * @return the DTO, or null if the input is null
     */
    public StudentSnapshotDto toStudentSnapshotDto(StudentSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        StudentSnapshotDto dto = new StudentSnapshotDto();
        dto.setCodeNumber(snapshot.getCodeNumber());
        dto.setFullName(snapshot.getFullName());
        dto.setEmail(snapshot.getEmail());
        dto.setWhatsappNumber(snapshot.getWhatsappNumber());
        dto.setSecondaryPhoneNumber(snapshot.getSecondaryPhoneNumber());
        dto.setAddress(snapshot.getAddress());
        dto.setNic(snapshot.getNic());
        dto.setSchool(snapshot.getSchool());
        dto.setPaperWritingMode(snapshot.getPaperWritingMode());
        dto.setPaperCenterId(snapshot.getPaperCenterId());
        dto.setStudyMedium(snapshot.getStudyMedium());
        return dto;
    }

    /**
     * Converts a StudentSnapshotDto to a StudentSnapshot entity.
     *
     * @param dto the DTO to convert, can be null
     * @return the entity, or null if the input is null
     */
    public StudentSnapshot toStudentSnapshotEntity(StudentSnapshotDto dto) {
        if (dto == null) {
            return null;
        }

        StudentSnapshot snapshot = new StudentSnapshot();
        snapshot.setCodeNumber(dto.getCodeNumber());
        snapshot.setFullName(dto.getFullName());
        snapshot.setEmail(dto.getEmail());
        snapshot.setWhatsappNumber(dto.getWhatsappNumber());
        snapshot.setSecondaryPhoneNumber(dto.getSecondaryPhoneNumber());
        snapshot.setAddress(dto.getAddress());
        snapshot.setNic(dto.getNic());
        snapshot.setSchool(dto.getSchool());
        snapshot.setPaperWritingMode(dto.getPaperWritingMode());
        snapshot.setPaperCenterId(dto.getPaperCenterId());
        snapshot.setStudyMedium(dto.getStudyMedium());
        return snapshot;
    }

}
