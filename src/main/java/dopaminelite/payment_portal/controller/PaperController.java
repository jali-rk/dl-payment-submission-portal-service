package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.calendar.CalendarEventAttendeeAddRequest;
import dopaminelite.payment_portal.dto.calendar.CalendarEventAttendeeDto;
import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.paper.MarkSchemeDto;
import dopaminelite.payment_portal.dto.paper.PaperCreateRequest;
import dopaminelite.payment_portal.dto.paper.PaperResponse;
import dopaminelite.payment_portal.dto.paper.PaperUpdateRequest;
import dopaminelite.payment_portal.dto.paper.PaperWindowFilter;
import dopaminelite.payment_portal.service.CalendarEventService;
import dopaminelite.payment_portal.service.PaperService;
import dopaminelite.payment_portal.util.JwtUserIdExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing papers.
 */
@RestController
@RequestMapping("/papers")
@RequiredArgsConstructor
public class PaperController {

    private static final UUID UNKNOWN_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PaperService paperService;
    private final CalendarEventService calendarEventService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * Lists papers with optional filtering and pagination.
     *
     * @param titleSearch case-insensitive substring match on title, optional
     * @param windowFilter filter by validity window (UPCOMING/ACTIVE/PAST), optional
     * @param limit maximum number of results per page, defaults to 20
     * @param offset number of results to skip, defaults to 0
     * @return paginated list of papers
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<PaperResponse>> listPapers(
            @RequestParam(required = false) String titleSearch,
            @RequestParam(required = false) PaperWindowFilter windowFilter,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        if (limit < 1 || limit > 100) {
            limit = 20;
        }

        PaginatedResponse<PaperResponse> response = paperService.listPapers(titleSearch, windowFilter, limit, offset);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new paper.
     *
     * @param request the paper creation request
     * @param authorizationHeader the caller's bearer token, used to attribute the creating admin
     * @return the created paper with HTTP 201 status
     * @throws dopaminelite.payment_portal.exception.ValidationException if the validity window is invalid
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if a linked portal ID does not exist
     */
    @PostMapping
    public ResponseEntity<PaperResponse> createPaper(
            @Valid @RequestBody PaperCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID adminId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_ADMIN_ID);
        PaperResponse response = paperService.createPaper(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a paper by its unique identifier.
     *
     * @param paperId the UUID of the paper to retrieve
     * @return the paper details
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID
     */
    @GetMapping("/{paperId}")
    public ResponseEntity<PaperResponse> getPaperById(@PathVariable UUID paperId) {
        PaperResponse response = paperService.getPaperById(paperId);
        return ResponseEntity.ok(response);
    }

    /**
     * Partially updates an existing paper.
     *
     * @param paperId the UUID of the paper to update
     * @param request the update request payload containing fields to modify
     * @return the updated paper
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID
     */
    @PatchMapping("/{paperId}")
    public ResponseEntity<PaperResponse> updatePaper(
            @PathVariable UUID paperId,
            @Valid @RequestBody PaperUpdateRequest request
    ) {
        PaperResponse response = paperService.updatePaper(paperId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Sets or replaces a paper's mark scheme (which sections apply and their max marks).
     * Locked once any mark has been recorded for the paper — delete all marks first to unlock.
     *
     * @param paperId the UUID of the paper
     * @param request the new mark scheme; at least one section must be set
     * @return the updated paper
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID
     * @throws dopaminelite.payment_portal.exception.ValidationException if the scheme has no section enabled, or the paper's scheme is locked
     */
    @PutMapping("/{paperId}/mark-scheme")
    public ResponseEntity<PaperResponse> updateMarkScheme(
            @PathVariable UUID paperId,
            @Valid @RequestBody MarkSchemeDto request
    ) {
        PaperResponse response = paperService.updateMarkScheme(paperId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a paper, provided nothing depends on it yet.
     *
     * @param paperId the UUID of the paper to delete
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID
     * @throws dopaminelite.payment_portal.exception.ValidationException if the paper has any paper slots and/or marks recorded against it
     */
    @DeleteMapping("/{paperId}")
    public ResponseEntity<Void> deletePaper(@PathVariable UUID paperId) {
        paperService.deletePaper(paperId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists the admin/main_admin users explicitly attached to a paper's shared calendar event.
     * Instructors are never listed here — they see every paper's event automatically and are
     * never represented as attendee rows.
     *
     * @param paperId the UUID of the paper
     * @return the current attendee list
     */
    @GetMapping("/{paperId}/event-attendees")
    public ResponseEntity<List<CalendarEventAttendeeDto>> listEventAttendees(@PathVariable UUID paperId) {
        return ResponseEntity.ok(calendarEventService.listAttendees(paperId));
    }

    /**
     * Attaches an admin/main_admin to a paper's shared calendar event, idempotently.
     *
     * @param paperId the UUID of the paper
     * @param request the user to attach, plus a display snapshot resolved by the BFF
     * @param authorizationHeader the caller's bearer token, used to attribute who added them
     * @return the attendee record with HTTP 201 status
     */
    @PostMapping("/{paperId}/event-attendees")
    public ResponseEntity<CalendarEventAttendeeDto> addEventAttendee(
            @PathVariable UUID paperId,
            @Valid @RequestBody CalendarEventAttendeeAddRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID adminId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_ADMIN_ID);
        CalendarEventAttendeeDto response = calendarEventService.addAdminAttendee(paperId, request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Detaches a user from a paper's shared calendar event.
     *
     * @param paperId the UUID of the paper
     * @param userId the UUID of the user to remove
     */
    @DeleteMapping("/{paperId}/event-attendees/{userId}")
    public ResponseEntity<Void> removeEventAttendee(@PathVariable UUID paperId, @PathVariable UUID userId) {
        calendarEventService.removeAdminAttendee(paperId, userId);
        return ResponseEntity.noContent().build();
    }

}
