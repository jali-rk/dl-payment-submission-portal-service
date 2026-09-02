package dopaminelite.payment_portal.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.paper.PaperSlotCreationResultDto;
import dopaminelite.payment_portal.dto.paper.PaperSlotResponse;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.enums.PaperSlotCreationOutcome;
import dopaminelite.payment_portal.entity.enums.PaperSlotStatus;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.PaperSlotMapper;
import dopaminelite.payment_portal.repository.PaperRepository;
import dopaminelite.payment_portal.repository.PaperSlotCreationResultRepository;
import dopaminelite.payment_portal.repository.PaperSlotRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating paper slot creation, scanning, consumption, listing, and QR
 * generation. Slot-creation writes are delegated to {@link PaperSlotWriter}, whose methods
 * are each their own isolated transaction — see that class's Javadoc for why.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaperSlotService {

    private static final ZoneId SRI_LANKA_ZONE = ZoneId.of("Asia/Colombo");
    private static final int QR_SIZE_PX = 300;

    private final PaperSlotRepository paperSlotRepository;
    private final PaperSlotCreationResultRepository resultRepository;
    private final PaperRepository paperRepository;
    private final PaymentSubmissionRepository paymentSubmissionRepository;
    private final PaperSlotMapper paperSlotMapper;
    private final PaperSlotWriter paperSlotWriter;

    /**
     * Creates (or re-attempts) a paper slot for every paper linked to the submission's portal.
     * Never throws for an individual paper's failure — each outcome is captured in the
     * returned list and persisted for later retrieval via {@link #getCreationResults}.
     *
     * @param submissionId the approved submission's ID
     * @return one result per linked paper
     * @throws ResourceNotFoundException if the submission does not exist
     * @throws ValidationException if the submission is not currently APPROVED
     */
    public List<PaperSlotCreationResultDto> createSlotsForApprovedSubmission(UUID submissionId) {
        PaymentSubmission submission = paymentSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment submission not found with id: " + submissionId));

        if (submission.getStatus() != SubmissionStatus.APPROVED) {
            throw new ValidationException("Cannot create paper slots: submission " + submissionId + " is not currently approved");
        }

        List<Paper> linkedPapers = paperRepository.findByLinkedPortalId(submission.getPortal().getId());
        linkedPapers.forEach(paper -> attemptCreateSlot(submissionId, paper.getId(), paper.getTitle()));

        // Build the return value from a fresh, eagerly-fetched read rather than the entities
        // returned by the REQUIRES_NEW writer calls above: each of those runs in its own
        // transaction that closes the moment it returns, so any lazy association on the
        // entity it hands back (e.g. slot.getPaper()) is bound to an already-closed session
        // and throws LazyInitializationException the moment something tries to read it here.
        // getCreationResults() re-reads everything through a query that eagerly joins what's
        // needed, sidestepping that entirely.
        return getCreationResults(submissionId);
    }

    private void attemptCreateSlot(UUID submissionId, UUID paperId, String paperTitle) {
        PaperSlot slot;
        PaperSlotCreationOutcome outcome;
        String message = null;

        try {
            PaperSlot inserted = paperSlotWriter.insertIfAbsent(submissionId, paperId);
            if (inserted != null) {
                slot = inserted;
                outcome = PaperSlotCreationOutcome.CREATED;
            } else {
                slot = paperSlotWriter.findExisting(paperId, submissionId);
                outcome = PaperSlotCreationOutcome.ALREADY_EXISTS;
            }
        } catch (DataIntegrityViolationException e) {
            // A concurrent attempt won the race between the pre-check and the insert.
            // The transaction that hit this is already aborted and rolling back; fetch the
            // winner's row in a brand new one rather than reusing anything from it.
            slot = paperSlotWriter.findExisting(paperId, submissionId);
            outcome = PaperSlotCreationOutcome.ALREADY_EXISTS;
        } catch (Exception e) {
            log.error("Failed to create paper slot for submission {} / paper {} ('{}'): {}",
                    submissionId, paperId, paperTitle, e.getMessage(), e);
            slot = null;
            outcome = PaperSlotCreationOutcome.FAILED;
            message = String.format(
                    "Could not create a slot for '%s' (submission %s). Please use retry, or contact support with this reference if it continues to fail.",
                    paperTitle, submissionId);
        }

        paperSlotWriter.upsertResult(submissionId, paperId, paperTitle, outcome, message, slot);
    }

    /**
     * Reads the persisted slot-creation results for a submission, independent of when the
     * approval that triggered them happened.
     *
     * @param submissionId the submission's ID
     * @return one entry per paper that has ever been attempted for this submission
     */
    public List<PaperSlotCreationResultDto> getCreationResults(UUID submissionId) {
        LocalDate today = LocalDate.now(SRI_LANKA_ZONE);
        return resultRepository.findByPaymentSubmissionId(submissionId).stream()
                .map(result -> paperSlotMapper.toResultDto(result, today))
                .toList();
    }

    /**
     * Read-only lookup by QR token, for an instructor to review before consuming.
     *
     * @param qrToken the token encoded in the slot's QR code
     * @return the slot's full details and current computed status
     * @throws ResourceNotFoundException if no slot has this token
     */
    public PaperSlotResponse getSlotByToken(UUID qrToken) {
        PaperSlot slot = paperSlotRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Paper slot not found for the given QR token"));

        return paperSlotMapper.toResponse(slot, LocalDate.now(SRI_LANKA_ZONE));
    }

    /**
     * Direct lookup of a slot by its internal ID. Used by the BFF to verify slot ownership
     * without paginating through a student's full slot list.
     *
     * @param slotId the slot's internal ID
     * @return the slot's full details and current computed status
     * @throws ResourceNotFoundException if no slot exists with the given ID
     */
    public PaperSlotResponse getSlotById(UUID slotId) {
        PaperSlot slot = paperSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper slot not found with id: " + slotId));

        return paperSlotMapper.toResponse(slot, LocalDate.now(SRI_LANKA_ZONE));
    }

    /**
     * Marks a slot as consumed (attendance taken), atomically and exactly once.
     *
     * @param qrToken the token encoded in the slot's QR code
     * @param instructorId the ID of the instructor performing the scan
     * @return the now-consumed slot's details
     * @throws ResourceNotFoundException if no slot has this token
     * @throws ValidationException if the slot is not currently AVAILABLE (already consumed,
     *         scheduled, or expired)
     */
    @Transactional
    public PaperSlotResponse consumeSlotByToken(UUID qrToken, UUID instructorId) {
        PaperSlot slot = paperSlotRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Paper slot not found for the given QR token"));

        LocalDateTime now = LocalDateTime.now(SRI_LANKA_ZONE);
        LocalDate today = now.toLocalDate();

        int updated = paperSlotRepository.consumeIfAvailable(slot.getId(), now, instructorId, today);

        if (updated == 0) {
            PaperSlot current = paperSlotRepository.findById(slot.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Paper slot not found"));
            PaperSlotStatus status = PaperSlotStatus.compute(
                    today, current.getPaper().getStartDate(), current.getPaper().getEndDate(), current.getConsumedAt());
            if (status == PaperSlotStatus.CONSUMED) {
                throw new ValidationException("This paper slot has already been consumed");
            }
            throw new ValidationException("This paper slot is not currently available (status: " + status + ")");
        }

        PaperSlot consumed = paperSlotRepository.findById(slot.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Paper slot not found"));
        return paperSlotMapper.toResponse(consumed, today);
    }

    /**
     * Lists slots matching optional filters, with status filtering done in SQL so pagination
     * counts stay accurate.
     */
    public PaginatedResponse<PaperSlotResponse> listSlots(
            UUID paperId, UUID studentId, String studentCodeNumber, PaperSlotStatus status, int limit, int offset) {
        Pageable pageable = PageRequest.of(offset / limit, limit);
        LocalDate today = LocalDate.now(SRI_LANKA_ZONE);

        Page<PaperSlot> page = paperSlotRepository.findByFilters(
                paperId, studentId, studentCodeNumber, status == null ? null : status.name(), today, pageable);

        List<PaperSlotResponse> items = page.getContent().stream()
                .map(slot -> paperSlotMapper.toResponse(slot, today))
                .toList();

        return new PaginatedResponse<>(items, page.getTotalElements());
    }

    /**
     * Generates a QR code PNG encoding the slot's opaque token as plain text.
     *
     * @param slotId the slot's internal ID
     * @return PNG image bytes
     * @throws ResourceNotFoundException if no slot exists with the given ID
     */
    public byte[] generateQrPng(UUID slotId) {
        PaperSlot slot = paperSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper slot not found with id: " + slotId));

        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    slot.getQrToken().toString(), BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate QR code for slot " + slotId, e);
        }
    }

}
