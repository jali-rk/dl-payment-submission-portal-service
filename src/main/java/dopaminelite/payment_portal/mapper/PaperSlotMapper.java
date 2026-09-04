package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.paper.PaperSlotCreationResultDto;
import dopaminelite.payment_portal.dto.paper.PaperSlotResponse;
import dopaminelite.payment_portal.dto.submission.PortalRefDto;
import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.PaperSlotCreationResult;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.enums.PaperSlotStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Mapper for converting PaperSlot and PaperSlotCreationResult entities to response DTOs.
 * Reuses {@link PaymentSubmissionMapper#toStudentSnapshotDto} rather than duplicating the
 * student-snapshot field mapping.
 */
@Component
@RequiredArgsConstructor
public class PaperSlotMapper {

    private final PaymentSubmissionMapper submissionMapper;

    /**
     * Converts a PaperSlot entity to a PaperSlotResponse DTO, computing its current status.
     *
     * @param slot the entity to convert, can be null
     * @param today the current date, used to compute the slot's status
     * @return the response DTO, or null if the input is null
     */
    public PaperSlotResponse toResponse(PaperSlot slot, LocalDate today) {
        if (slot == null) {
            return null;
        }

        PaymentSubmission submission = slot.getPaymentSubmission();

        PaperSlotResponse response = new PaperSlotResponse();
        response.setId(slot.getId());
        response.setQrToken(slot.getQrToken());
        response.setStatus(PaperSlotStatus.compute(
                today, slot.getPaper().getStartDate(), slot.getPaper().getEndDate(), slot.getConsumedAt()));
        response.setPaperId(slot.getPaper().getId());
        response.setPaperTitle(slot.getPaper().getTitle());
        response.setSubmissionId(submission.getId());
        response.setStudentId(submission.getStudentId());
        response.setStudent(submissionMapper.toStudentSnapshotDto(submission.getStudentSnapshot()));
        response.setPortal(new PortalRefDto(submission.getPortal().getId(), submission.getPortal().getDisplayName()));
        response.setConsumedAt(slot.getConsumedAt());
        response.setConsumedByInstructorId(slot.getConsumedByInstructorId());
        response.setCreatedAt(slot.getCreatedAt());

        return response;
    }

    /**
     * Converts a PaperSlotCreationResult entity to a PaperSlotCreationResultDto, nesting the
     * full slot details when one exists (outcome CREATED or ALREADY_EXISTS).
     *
     * @param result the entity to convert, can be null
     * @param today the current date, used to compute the nested slot's status
     * @return the response DTO, or null if the input is null
     */
    public PaperSlotCreationResultDto toResultDto(PaperSlotCreationResult result, LocalDate today) {
        if (result == null) {
            return null;
        }

        PaperSlotCreationResultDto dto = new PaperSlotCreationResultDto();
        dto.setPaperId(result.getPaper().getId());
        dto.setPaperTitle(result.getPaperTitleSnapshot());
        dto.setOutcome(result.getOutcome());
        dto.setMessage(result.getMessage());
        dto.setSlot(toResponse(result.getPaperSlot(), today));
        dto.setAttemptedAt(result.getAttemptedAt());

        return dto;
    }

}
