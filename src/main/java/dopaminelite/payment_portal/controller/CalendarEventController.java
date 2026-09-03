package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.calendar.CalendarEventCreateRequest;
import dopaminelite.payment_portal.dto.calendar.CalendarEventResponse;
import dopaminelite.payment_portal.dto.calendar.CalendarEventUpdateRequest;
import dopaminelite.payment_portal.dto.calendar.CreateClassEventRequest;
import dopaminelite.payment_portal.dto.calendar.SyncClassAttendeesRequest;
import dopaminelite.payment_portal.service.CalendarEventService;
import dopaminelite.payment_portal.util.JwtUserIdExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for calendar events: full CRUD over {@code USER} events (owner-only),
 * scheduling {@code CLASS} events (create is MAIN_ADMIN-only, enforced upstream by the BFF;
 * edit/delete is any MAIN_ADMIN, enforced here), and listing every event visible to the caller.
 * {@code PAPER}-sourced events have no create/update/delete path here at all — see
 * {@code CalendarEventService}.
 */
@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarEventController {

    private static final UUID UNKNOWN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final CalendarEventService calendarEventService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * Lists every event visible to the caller within a date range.
     *
     * @param from first date of the range (inclusive)
     * @param to last date of the range (inclusive)
     * @param role the caller's role, supplied by the BFF from its own auth context — enables
     *        the blanket "instructor sees every paper event"/"student sees every global class
     *        event" visibility rules
     * @param authorizationHeader the caller's bearer token
     * @return matching events, soonest first
     */
    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventResponse>> listEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String role,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID userId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_USER_ID);
        return ResponseEntity.ok(calendarEventService.listForUser(userId, role, from, to));
    }

    /**
     * Creates a new personal calendar event owned by the caller.
     *
     * @param request the event details
     * @param authorizationHeader the caller's bearer token, used to attribute ownership
     * @return the created event with HTTP 201 status
     */
    @PostMapping("/events")
    public ResponseEntity<CalendarEventResponse> createEvent(
            @Valid @RequestBody CalendarEventCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID userId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_USER_ID);
        CalendarEventResponse response = calendarEventService.createUserEvent(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Schedules a class as a calendar event — global, or scoped to one or more classes (the
     * BFF has already resolved which classes to which student IDs by this point).
     *
     * @param request the event details, audience, and (for a class-scoped event) resolved
     *        class references and student IDs
     * @param authorizationHeader the caller's bearer token, used to attribute the scheduling admin
     * @return the created event with HTTP 201 status
     */
    @PostMapping("/events/class")
    public ResponseEntity<CalendarEventResponse> createClassEvent(
            @Valid @RequestBody CreateClassEventRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID adminId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_USER_ID);
        CalendarEventResponse response = calendarEventService.createClassEvent(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Partially updates a personal or scheduled-class calendar event.
     *
     * @param eventId the event's ID
     * @param request the fields to update
     * @param role the caller's role, supplied by the BFF — needed for the {@code CLASS}
     *        any-MAIN_ADMIN permission check
     * @param authorizationHeader the caller's bearer token
     * @return the updated event
     * @throws dopaminelite.payment_portal.exception.ValidationException if the event is a
     *         {@code PAPER} event, isn't owned by the caller (for a {@code USER} event), or the
     *         caller isn't a MAIN_ADMIN (for a {@code CLASS} event)
     */
    @PatchMapping("/events/{eventId}")
    public ResponseEntity<CalendarEventResponse> updateEvent(
            @PathVariable UUID eventId,
            @RequestBody CalendarEventUpdateRequest request,
            @RequestParam(required = false) String role,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID userId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_USER_ID);
        return ResponseEntity.ok(calendarEventService.updateEvent(userId, role, eventId, request));
    }

    /**
     * Deletes a personal or scheduled-class calendar event.
     *
     * @param eventId the event's ID
     * @param role the caller's role, supplied by the BFF — needed for the {@code CLASS}
     *        any-MAIN_ADMIN permission check
     * @param authorizationHeader the caller's bearer token
     * @throws dopaminelite.payment_portal.exception.ValidationException if the event is a
     *         {@code PAPER} event, isn't owned by the caller (for a {@code USER} event), or the
     *         caller isn't a MAIN_ADMIN (for a {@code CLASS} event)
     */
    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable UUID eventId,
            @RequestParam(required = false) String role,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID userId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_USER_ID);
        calendarEventService.deleteEvent(userId, role, eventId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Attaches newly-enrolled students to every calendar event linked to a class, idempotently
     * — called by the BFF as a side effect of its existing add-student-to-class action. Same
     * trust model as every other endpoint here (JWT-derived caller ID only; the BFF is what
     * restricts who can reach this).
     *
     * @param classId the class (`videoms` folder) whose enrollment changed
     * @param request the students to ensure are attached
     */
    @PostMapping("/classes/{classId}/sync-attendees")
    public ResponseEntity<Void> syncClassAttendees(
            @PathVariable String classId,
            @RequestBody SyncClassAttendeesRequest request
    ) {
        calendarEventService.syncClassEnrollmentToEvents(classId, request.getStudentIds());
        return ResponseEntity.noContent().build();
    }

}
