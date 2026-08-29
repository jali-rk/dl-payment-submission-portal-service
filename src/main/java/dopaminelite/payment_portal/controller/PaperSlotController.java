package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.paper.PaperSlotCreationResultDto;
import dopaminelite.payment_portal.dto.paper.PaperSlotResponse;
import dopaminelite.payment_portal.entity.enums.PaperSlotStatus;
import dopaminelite.payment_portal.service.PaperSlotService;
import dopaminelite.payment_portal.util.JwtUserIdExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for paper slot scanning, consumption, QR generation, and listing.
 */
@RestController
@RequestMapping("/paper-slots")
@RequiredArgsConstructor
public class PaperSlotController {

    private static final UUID UNKNOWN_INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PaperSlotService paperSlotService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * Lists paper slots with optional filtering and pagination. Used both by admins auditing
     * a paper and by students looking up all of their own slots (by studentId or code number).
     *
     * @param paperId filter by paper, optional
     * @param studentId filter by the submitting student's ID, optional
     * @param studentCodeNumber filter by the submitting student's code number, optional
     * @param status filter by computed status, optional
     * @param limit maximum number of results per page, defaults to 20
     * @param offset number of results to skip, defaults to 0
     * @return paginated list of paper slots
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<PaperSlotResponse>> listSlots(
            @RequestParam(required = false) UUID paperId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) String studentCodeNumber,
            @RequestParam(required = false) PaperSlotStatus status,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        if (limit < 1 || limit > 100) {
            limit = 20;
        }

        PaginatedResponse<PaperSlotResponse> response =
                paperSlotService.listSlots(paperId, studentId, studentCodeNumber, status, limit, offset);
        return ResponseEntity.ok(response);
    }

    /**
     * Direct lookup of a slot by its internal ID. Lets a caller (the BFF) verify who owns a
     * slot without paginating through a list.
     *
     * @param slotId the slot's internal ID
     * @return the slot's full details and current computed status
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no slot exists with the given ID
     */
    @GetMapping("/{slotId}")
    public ResponseEntity<PaperSlotResponse> getSlot(@PathVariable UUID slotId) {
        PaperSlotResponse response = paperSlotService.getSlotById(slotId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a QR code PNG encoding the slot's opaque token. Usable by an admin screen or a
     * student downloading their own code to bring to the paper center.
     *
     * @param slotId the slot's internal ID
     * @return PNG image bytes
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no slot exists with the given ID
     */
    @GetMapping("/{slotId}/qr-code")
    public ResponseEntity<byte[]> getQrCode(@PathVariable UUID slotId) {
        byte[] png = paperSlotService.generateQrPng(slotId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(png);
    }

    /**
     * Read-only lookup of a slot by its scanned QR token. Lets an instructor review the
     * student and payment details before confirming attendance.
     *
     * @param qrToken the token encoded in the slot's QR code
     * @return the slot's full details and current computed status
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no slot has this token
     */
    @GetMapping("/scan/{qrToken}")
    public ResponseEntity<PaperSlotResponse> scanSlot(@PathVariable UUID qrToken) {
        PaperSlotResponse response = paperSlotService.getSlotByToken(qrToken);
        return ResponseEntity.ok(response);
    }

    /**
     * Marks a slot as consumed (attendance taken). Safe under concurrent double-scans — only
     * one call for a given slot can ever succeed.
     *
     * @param qrToken the token encoded in the slot's QR code
     * @param authorizationHeader the caller's bearer token, used to attribute the consuming instructor
     * @return the now-consumed slot's details
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no slot has this token
     * @throws dopaminelite.payment_portal.exception.ValidationException if the slot is not currently available
     */
    @PostMapping("/scan/{qrToken}/consume")
    public ResponseEntity<PaperSlotResponse> consumeSlot(
            @PathVariable UUID qrToken,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID instructorId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_INSTRUCTOR_ID);
        PaperSlotResponse response = paperSlotService.consumeSlotByToken(qrToken, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reads the persisted slot-creation results for a submission, independent of when the
     * approval that triggered them happened. Shows both successful slots (with full details)
     * and failed attempts (with a human-readable reason).
     *
     * @param submissionId the submission's ID
     * @return one entry per paper that has ever been attempted for this submission
     */
    @GetMapping("/creation-results/{submissionId}")
    public ResponseEntity<List<PaperSlotCreationResultDto>> getCreationResults(@PathVariable UUID submissionId) {
        List<PaperSlotCreationResultDto> results = paperSlotService.getCreationResults(submissionId);
        return ResponseEntity.ok(results);
    }

    /**
     * Re-attempts slot creation for a submission's linked papers — recovers from a previously
     * reported FAILED result. Idempotent: papers already created come back as ALREADY_EXISTS.
     *
     * @param submissionId the submission's ID
     * @return the fresh result list
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if the submission does not exist
     * @throws dopaminelite.payment_portal.exception.ValidationException if the submission is not currently approved
     */
    @PostMapping("/retry/{submissionId}")
    public ResponseEntity<List<PaperSlotCreationResultDto>> retry(@PathVariable UUID submissionId) {
        List<PaperSlotCreationResultDto> results = paperSlotService.createSlotsForApprovedSubmission(submissionId);
        return ResponseEntity.ok(results);
    }

}
