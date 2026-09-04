package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.calendar.CalendarEventAttendeeAddRequest;
import dopaminelite.payment_portal.dto.calendar.CalendarEventAttendeeDto;
import dopaminelite.payment_portal.dto.calendar.CalendarEventCreateRequest;
import dopaminelite.payment_portal.dto.calendar.CalendarEventResponse;
import dopaminelite.payment_portal.dto.calendar.CalendarEventUpdateRequest;
import dopaminelite.payment_portal.dto.calendar.CreateClassEventRequest;
import dopaminelite.payment_portal.entity.CalendarEvent;
import dopaminelite.payment_portal.entity.CalendarEventAttendee;
import dopaminelite.payment_portal.entity.CalendarEventClass;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.enums.CalendarSourceType;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.CalendarEventMapper;
import dopaminelite.payment_portal.repository.CalendarEventAttendeeRepository;
import dopaminelite.payment_portal.repository.CalendarEventClassRepository;
import dopaminelite.payment_portal.repository.CalendarEventRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for calendar events: full CRUD over {@code USER} events (owner-only), and the
 * lifecycle of the single shared {@code PAPER} event per paper (system-managed only — see
 * {@link CalendarSourceType}).
 *
 * <p><b>Visibility model</b> (see {@link #listForUser}): a {@code PAPER} event is not "owned"
 * by anyone. Instructors see every {@code PAPER} event as a blanket role permission, computed
 * live — no stored row, because an instructor can already mark any paper today. Students and
 * admins see one only once a {@link CalendarEventAttendee} row attaches them: a student is
 * attached automatically the moment their approved payment creates a paper slot (see
 * {@link #attachStudentToPaperEvent}); an admin is attached explicitly via
 * {@link #addAdminAttendee}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {

    /**
     * Preset colors a user may choose for their own events. Deliberately not free-form hex.
     */
    public static final Set<String> USER_COLOR_PALETTE = Set.of(
            "blue", "green", "purple", "red", "orange", "yellow", "teal", "pink");

    /**
     * Fixed, non-choosable color for every {@code PAPER} event, so they're visually distinct
     * from personal events at a glance.
     */
    private static final String PAPER_EVENT_COLOR = "indigo";

    /**
     * Whole-second end-of-day, used instead of {@code LocalTime.MAX} (23:59:59.999999999):
     * that nanosecond-precision value gets rounded — not truncated — when Postgres stores it
     * into a {@code timestamp(6)} column, which rounds it up into 00:00:00 the *next* day.
     */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventAttendeeRepository attendeeRepository;
    private final CalendarEventClassRepository calendarEventClassRepository;
    private final CalendarEventMapper calendarEventMapper;
    private final PaperRepository paperRepository;

    /**
     * Lists every event visible to a user within a date range: events they own, events they
     * attend, every {@code PAPER} event unconditionally if they're an instructor, every global
     * {@code CLASS} event unconditionally if they're a student, and every {@code CLASS} event
     * (global or class-scoped) unconditionally if they're a MAIN_ADMIN.
     *
     * @param userId the caller's ID
     * @param role the caller's role (as reported by the BFF's auth context); the blanket
     *        visibility rules above only apply when this matches exactly
     * @param from first date of the range (inclusive)
     * @param to last date of the range (inclusive)
     * @return matching events, soonest first
     */
    public List<CalendarEventResponse> listForUser(UUID userId, String role, LocalDate from, LocalDate to) {
        LocalDateTime rangeStart = from.atStartOfDay();
        LocalDateTime rangeEnd = to.atTime(LocalTime.MAX);

        Map<UUID, CalendarEvent> events = new LinkedHashMap<>();

        calendarEventRepository.findOwnedInRange(CalendarSourceType.USER, userId, rangeStart, rangeEnd)
                .forEach(e -> events.put(e.getId(), e));
        calendarEventRepository.findAttendingInRange(userId, rangeStart, rangeEnd)
                .forEach(e -> events.put(e.getId(), e));
        if ("INSTRUCTOR".equalsIgnoreCase(role)) {
            calendarEventRepository.findBySourceTypeInRange(CalendarSourceType.PAPER, rangeStart, rangeEnd)
                    .forEach(e -> events.put(e.getId(), e));
        }
        if ("STUDENT".equalsIgnoreCase(role)) {
            calendarEventRepository.findGlobalClassEventsInRange(rangeStart, rangeEnd)
                    .forEach(e -> events.put(e.getId(), e));
        }
        if ("MAIN_ADMIN".equalsIgnoreCase(role)) {
            // Every CLASS event, global or class-scoped — not just global. Any MAIN_ADMIN can
            // edit/delete any of them (see getManageableEvent), so they all need to be
            // discoverable here too, otherwise that permission has no way to be exercised
            // through the calendar UI at all — a scheduled class's creator wouldn't even see
            // their own event on their own calendar.
            calendarEventRepository.findBySourceTypeInRange(CalendarSourceType.CLASS, rangeStart, rangeEnd)
                    .forEach(e -> events.put(e.getId(), e));
        }

        return events.values().stream()
                .sorted(Comparator.comparing(CalendarEvent::getStartAt))
                .map(calendarEventMapper::toResponse)
                .toList();
    }

    @Transactional
    public CalendarEventResponse createUserEvent(UUID ownerId, CalendarEventCreateRequest request) {
        validateColor(request.getColor());
        validateRange(request.getStartAt(), request.getEndAt());

        CalendarEvent event = new CalendarEvent();
        event.setSourceType(CalendarSourceType.USER);
        event.setOwnerId(ownerId);
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setStartAt(request.getStartAt());
        event.setEndAt(request.getEndAt());
        event.setAllDay(request.isAllDay());
        event.setColor(request.getColor());

        return calendarEventMapper.toResponse(calendarEventRepository.save(event));
    }

    /**
     * Updates a {@code USER} event (owner-only) or a {@code CLASS} event (any MAIN_ADMIN) —
     * {@code PAPER} events have no edit path at all. Audience (global/classes) isn't editable
     * here for {@code CLASS} events, only the same fields a personal event exposes; changing
     * audience means deleting and recreating the event.
     *
     * @param callerId the caller's ID
     * @param callerRole the caller's role, needed for the {@code CLASS} permission check
     * @param eventId the event's ID
     * @param request the fields to update
     */
    @Transactional
    public CalendarEventResponse updateEvent(UUID callerId, String callerRole, UUID eventId, CalendarEventUpdateRequest request) {
        CalendarEvent event = getManageableEvent(callerId, callerRole, eventId);

        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getClassLink() != null) {
            event.setClassLink(normalizeClassLink(request.getClassLink()));
        }
        if (request.getStartAt() != null) {
            event.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            event.setEndAt(request.getEndAt());
        }
        if (request.getAllDay() != null) {
            event.setAllDay(request.getAllDay());
        }
        if (request.getColor() != null) {
            validateColor(request.getColor());
            event.setColor(request.getColor());
        }
        validateRange(event.getStartAt(), event.getEndAt());

        return calendarEventMapper.toResponse(calendarEventRepository.save(event));
    }

    /**
     * Deletes a {@code USER} event (owner-only) or a {@code CLASS} event (any MAIN_ADMIN).
     *
     * @param callerId the caller's ID
     * @param callerRole the caller's role, needed for the {@code CLASS} permission check
     * @param eventId the event's ID
     */
    @Transactional
    public void deleteEvent(UUID callerId, String callerRole, UUID eventId) {
        CalendarEvent event = getManageableEvent(callerId, callerRole, eventId);
        calendarEventRepository.delete(event);
    }

    private CalendarEvent getManageableEvent(UUID callerId, String callerRole, UUID eventId) {
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar event not found with id: " + eventId));

        switch (event.getSourceType()) {
            case PAPER -> throw ValidationException.calendarEventNotUserManaged(eventId);
            case USER -> {
                if (callerId == null || !callerId.equals(event.getOwnerId())) {
                    throw ValidationException.calendarEventNotOwnedByCaller(eventId);
                }
            }
            case CLASS -> {
                if (!"MAIN_ADMIN".equalsIgnoreCase(callerRole)) {
                    throw ValidationException.calendarEventNotManageableByCaller(eventId);
                }
            }
        }
        return event;
    }

    private void validateColor(String color) {
        if (!USER_COLOR_PALETTE.contains(color)) {
            throw ValidationException.invalidCalendarEventColor(color);
        }
    }

    /**
     * Validates a class link is a well-formed, browsable URL — just enough to catch typos
     * before they end up in front of students as a dead "Join Class" button, not full URL
     * validation — and collapses a blank value to {@code null} ("no link").
     *
     * <p>Blank-to-{@code null} normalization happens here rather than being left to the
     * caller specifically so {@link #updateEvent} can clear an existing link: its PATCH
     * semantics treat a {@code null} field on the *request* as "not provided, don't touch",
     * so a request field that's merely blank (e.g. the caller cleared a text input, which
     * naturally serializes as {@code ""} rather than omitting the field) must still normalize
     * to an actual {@code null} being written to the entity, or the old link would silently
     * survive.
     *
     * @param classLink the raw value from the request, or null
     * @return the trimmed link, or null if blank
     */
    private String normalizeClassLink(String classLink) {
        if (classLink == null || classLink.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(classLink.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme) || uri.getHost() == null) {
                throw ValidationException.invalidCalendarEventClassLink(classLink);
            }
        } catch (URISyntaxException e) {
            throw ValidationException.invalidCalendarEventClassLink(classLink);
        }
        return classLink.trim();
    }

    private void validateRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt.isAfter(endAt)) {
            throw new ValidationException("Event start time must not be after its end time");
        }
    }

    // ---- PAPER event lifecycle — called only from PaperService / PaperSlotService ----

    /**
     * Creates the single shared {@code PAPER} event for a paper, immediately at paper-creation
     * time. Deliberately joins the caller's own transaction (plain {@code @Transactional}, not
     * {@code REQUIRES_NEW}) — {@code paper} is still unsaved-or-uncommitted at the call site in
     * {@code PaperService.createPaper}, so a separate transaction wouldn't yet see its row to
     * satisfy the FK; joining the same transaction also means the paper and its event are
     * created atomically, which is what we want here (unlike the slot-approval attach path
     * below, which deliberately does isolate itself — see {@link #attachStudentToPaperEvent}).
     *
     * @param paper the paper being created (already {@code save()}d, but not yet committed)
     */
    @Transactional
    public void createPaperEvent(Paper paper) {
        if (calendarEventRepository.existsBySourcePaperId(paper.getId())) {
            return;
        }
        calendarEventRepository.save(buildPaperEvent(paper));
    }

    /**
     * Updates the paper's one event's dates when the paper's own dates change. Joins the
     * caller's transaction, like {@link #createPaperEvent} — a paper-date edit and its calendar
     * sync should commit or roll back together (unlike the slot-approval attach path, which
     * deliberately does isolate itself; see {@link #attachStudentToPaperEvent}).
     *
     * @param paperId the paper whose dates changed
     * @param newStart the paper's new start date
     * @param newEnd the paper's new end date
     */
    @Transactional
    public void syncPaperDates(UUID paperId, LocalDate newStart, LocalDate newEnd) {
        calendarEventRepository.findBySourcePaperId(paperId).ifPresent(event -> {
            event.setStartAt(newStart.atStartOfDay());
            event.setEndAt(newEnd.atTime(END_OF_DAY));
            calendarEventRepository.save(event);
        });
    }

    /**
     * Attaches a student to their paper's shared event, idempotently. Called as an *additional*
     * step alongside slot creation — never touches {@code PaperSlot}. Runs in its own
     * {@code REQUIRES_NEW} transaction for the same reason as {@link PaperSlotWriter}'s writes:
     * this is invoked from within {@code PaperSlotService.createSlotsForApprovedSubmission}'s
     * per-paper loop, which must stay resilient to one paper's failure here not affecting any
     * other paper's slot/event handling in the same submission.
     *
     * @param paper the paper the student is now eligible for
     * @param studentId the student's ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachStudentToPaperEvent(Paper paper, UUID studentId) {
        CalendarEvent event = calendarEventRepository.findBySourcePaperId(paper.getId())
                .orElseGet(() -> calendarEventRepository.save(buildPaperEvent(paper)));

        if (attendeeRepository.existsByEventIdAndUserId(event.getId(), studentId)) {
            return;
        }

        CalendarEventAttendee attendee = new CalendarEventAttendee();
        attendee.setEvent(event);
        attendee.setUserId(studentId);
        attendeeRepository.save(attendee);
    }

    private CalendarEvent buildPaperEvent(Paper paper) {
        CalendarEvent event = new CalendarEvent();
        event.setSourceType(CalendarSourceType.PAPER);
        event.setSourcePaper(paper);
        event.setTitle(paper.getTitle());
        event.setAllDay(true);
        event.setColor(PAPER_EVENT_COLOR);
        event.setStartAt(paper.getStartDate().atStartOfDay());
        event.setEndAt(paper.getEndDate().atTime(END_OF_DAY));
        return event;
    }

    // ---- CLASS event lifecycle — scheduled classes, created explicitly by a MAIN_ADMIN ----

    /**
     * Schedules a class as a calendar event. Exactly one of {@code request.isGlobal()} or a
     * non-empty {@code request.getClasses()} is required. For a non-global request, this
     * backend never resolves membership itself — it just attaches whichever UUIDs the BFF
     * already resolved and handed over in {@code request.getStudentIds()}.
     *
     * @param request the event details, audience, and (for a class-scoped event) the
     *        BFF-resolved class references and student IDs to attach
     * @param createdByAdminId the scheduling admin's ID (attribution only — edit/delete is
     *        role-based, not tied to this specific admin)
     * @return the created event
     * @throws ValidationException if the audience is neither purely global nor purely
     *         class-scoped
     */
    @Transactional
    public CalendarEventResponse createClassEvent(CreateClassEventRequest request, UUID createdByAdminId) {
        boolean hasClasses = request.getClasses() != null && !request.getClasses().isEmpty();
        if (request.isGlobal() == hasClasses) {
            throw ValidationException.classEventAudienceInvalid();
        }
        validateColor(request.getColor());
        validateRange(request.getStartAt(), request.getEndAt());

        CalendarEvent event = new CalendarEvent();
        event.setSourceType(CalendarSourceType.CLASS);
        event.setOwnerId(createdByAdminId);
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setClassLink(normalizeClassLink(request.getClassLink()));
        event.setStartAt(request.getStartAt());
        event.setEndAt(request.getEndAt());
        event.setAllDay(request.isAllDay());
        event.setColor(request.getColor());
        event.setGlobal(request.isGlobal());
        CalendarEvent savedEvent = calendarEventRepository.save(event);

        if (hasClasses) {
            List<CalendarEventClass> links = request.getClasses().stream().map(classRef -> {
                CalendarEventClass link = new CalendarEventClass();
                link.setEvent(savedEvent);
                link.setClassId(classRef.getId());
                link.setClassName(classRef.getName());
                return link;
            }).toList();
            calendarEventClassRepository.saveAll(links);

            if (request.getStudentIds() != null) {
                // A brand-new event has no existing attendees yet — no existence check needed,
                // just distinct + bulk insert (this list can realistically run into the hundreds).
                List<CalendarEventAttendee> attendees = request.getStudentIds().stream().distinct().map(studentId -> {
                    CalendarEventAttendee attendee = new CalendarEventAttendee();
                    attendee.setEvent(savedEvent);
                    attendee.setUserId(studentId);
                    attendee.setAddedByUserId(createdByAdminId);
                    return attendee;
                }).toList();
                attendeeRepository.saveAll(attendees);
            }
        }

        return calendarEventMapper.toResponse(savedEvent);
    }

    /**
     * Attaches newly-enrolled students to every calendar event linked to a class, idempotently
     * — called by the BFF as a side effect of its existing add-student-to-class action, so a
     * scheduled class's audience stays continuously accurate as class enrollment changes
     * (mirroring how {@link #attachStudentToPaperEvent} keeps a paper's audience accurate as
     * payments get approved). Safe to call with a student who's already attached — a no-op.
     *
     * @param classId the class (`videoms` folder) whose enrollment changed
     * @param studentIds the students to ensure are attached to every event linked to that class
     */
    @Transactional
    public void syncClassEnrollmentToEvents(String classId, List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }
        List<UUID> candidateIds = studentIds.stream().distinct().toList();
        List<CalendarEvent> linkedEvents = calendarEventClassRepository.findEventsByClassId(classId);

        for (CalendarEvent event : linkedEvents) {
            // One existing-attendees query per event, not one EXISTS check per candidate
            // student — a class enrollment sync can realistically involve hundreds of students.
            Set<UUID> alreadyAttached = new HashSet<>(attendeeRepository.findUserIdsByEventId(event.getId()));
            List<CalendarEventAttendee> newAttendees = candidateIds.stream()
                    .filter(studentId -> !alreadyAttached.contains(studentId))
                    .map(studentId -> {
                        CalendarEventAttendee attendee = new CalendarEventAttendee();
                        attendee.setEvent(event);
                        attendee.setUserId(studentId);
                        return attendee;
                    })
                    .toList();
            if (!newAttendees.isEmpty()) {
                attendeeRepository.saveAll(newAttendees);
            }
        }
    }

    // ---- Admin attendee management — backs the Papers admin "Manage Attendees" picker ----

    @Transactional
    public List<CalendarEventAttendeeDto> listAttendees(UUID paperId) {
        CalendarEvent event = getOrCreatePaperEvent(paperId);
        return attendeeRepository.findByEventIdAndAddedByUserIdIsNotNull(event.getId()).stream()
                .map(calendarEventMapper::toAttendeeDto)
                .toList();
    }

    @Transactional
    public CalendarEventAttendeeDto addAdminAttendee(UUID paperId, CalendarEventAttendeeAddRequest request, UUID addedByUserId) {
        CalendarEvent event = getOrCreatePaperEvent(paperId);

        CalendarEventAttendee attendee = attendeeRepository.findByEventIdAndUserId(event.getId(), request.getUserId())
                .orElseGet(CalendarEventAttendee::new);
        attendee.setEvent(event);
        attendee.setUserId(request.getUserId());
        attendee.setEmail(request.getEmail());
        attendee.setFullName(request.getFullName());
        attendee.setRole(request.getRole());
        attendee.setAddedByUserId(addedByUserId);

        return calendarEventMapper.toAttendeeDto(attendeeRepository.save(attendee));
    }

    @Transactional
    public void removeAdminAttendee(UUID paperId, UUID userId) {
        CalendarEvent event = getOrCreatePaperEvent(paperId);
        attendeeRepository.deleteAdminAddedByEventIdAndUserId(event.getId(), userId);
    }

    /**
     * Resilience for papers created before this feature shipped (no backfill migration was
     * run): lazily creates the paper's event on first admin-attendee-management access, the
     * same fallback {@link #attachStudentToPaperEvent} already relies on for the slot-approval
     * path. Every paper created going forward already has one from {@link #createPaperEvent}.
     */
    private CalendarEvent getOrCreatePaperEvent(UUID paperId) {
        return calendarEventRepository.findBySourcePaperId(paperId)
                .orElseGet(() -> {
                    Paper paper = paperRepository.findById(paperId)
                            .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));
                    return calendarEventRepository.save(buildPaperEvent(paper));
                });
    }

}
