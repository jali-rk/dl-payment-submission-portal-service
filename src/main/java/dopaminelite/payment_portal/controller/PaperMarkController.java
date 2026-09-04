package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.paper.PaperMarkCreateRequest;
import dopaminelite.payment_portal.dto.paper.PaperMarkResponse;
import dopaminelite.payment_portal.dto.paper.PaperMarkUpdateRequest;
import dopaminelite.payment_portal.dto.paper.StudentVerificationResponse;
import dopaminelite.payment_portal.service.PaperMarkService;
import dopaminelite.payment_portal.util.JwtUserIdExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for instructor-entered paper marks: verifying a student before entry, and
 * CRUD over the resulting mark records. Independent of {@code PaperSlotController} — marks are
 * not tied to a payment submission or slot.
 */
@RestController
@RequestMapping("/papers/{paperId}/marks")
@RequiredArgsConstructor
public class PaperMarkController {

    private static final UUID UNKNOWN_INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PaperMarkService paperMarkService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * Lists marks recorded for a paper, with pagination.
     *
     * @param paperId the UUID of the paper
     * @param limit maximum number of results per page, defaults to 20
     * @param offset number of results to skip, defaults to 0
     * @return paginated list of marks
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<PaperMarkResponse>> listMarks(
            @PathVariable UUID paperId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        if (limit < 1 || limit > 100) {
            limit = 20;
        }

        PaginatedResponse<PaperMarkResponse> response = paperMarkService.listMarks(paperId, limit, offset);
        return ResponseEntity.ok(response);
    }

    /**
     * Verifies a student exists (by code number) before an instructor fills in the marks form,
     * and reports whether that student already has a mark on this paper.
     *
     * @param paperId the UUID of the paper
     * @param studentCodeNumber the code number to verify
     * @return the resolved student's public data plus whether they already have a mark here
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID, or no student exists with that code number
     */
    @GetMapping("/verify-student")
    public ResponseEntity<StudentVerificationResponse> verifyStudent(
            @PathVariable UUID paperId,
            @RequestParam String studentCodeNumber
    ) {
        StudentVerificationResponse response = paperMarkService.verifyStudent(paperId, studentCodeNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * Records a new mark for a student on a paper.
     *
     * @param paperId the UUID of the paper
     * @param request the mark values and the student's code number
     * @param authorizationHeader the caller's bearer token, used to attribute the entering instructor
     * @return the created mark with HTTP 201 status
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID, or no student exists with the given code number
     * @throws dopaminelite.payment_portal.exception.ValidationException if the paper has no mark scheme configured, or a section mark is missing/out-of-bounds/provided-for-a-disabled-section
     * @throws dopaminelite.payment_portal.exception.DuplicateResourceException if this student already has a mark on this paper
     */
    @PostMapping
    public ResponseEntity<PaperMarkResponse> createMark(
            @PathVariable UUID paperId,
            @Valid @RequestBody PaperMarkCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID instructorId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_INSTRUCTOR_ID);
        PaperMarkResponse response = paperMarkService.createMark(paperId, request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Partially updates an existing mark's values. The student a mark belongs to is never
     * editable here.
     *
     * @param paperId the UUID of the paper
     * @param markId the UUID of the mark to update
     * @param request the fields to update
     * @param authorizationHeader the caller's bearer token, used to attribute the editing instructor
     * @return the updated mark
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no mark exists with the given ID for that paper
     * @throws dopaminelite.payment_portal.exception.ValidationException if an updated value is out-of-bounds or for a disabled section
     */
    @PatchMapping("/{markId}")
    public ResponseEntity<PaperMarkResponse> updateMark(
            @PathVariable UUID paperId,
            @PathVariable UUID markId,
            @RequestBody PaperMarkUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID instructorId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_INSTRUCTOR_ID);
        PaperMarkResponse response = paperMarkService.updateMark(paperId, markId, request, instructorId);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a mark. This is also the mechanism to bring a paper's mark count back to 0,
     * unlocking its mark scheme for editing again.
     *
     * @param paperId the UUID of the paper
     * @param markId the UUID of the mark to delete
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no mark exists with the given ID for that paper
     */
    @DeleteMapping("/{markId}")
    public ResponseEntity<Void> deleteMark(@PathVariable UUID paperId, @PathVariable UUID markId) {
        paperMarkService.deleteMark(paperId, markId);
        return ResponseEntity.noContent().build();
    }

}
