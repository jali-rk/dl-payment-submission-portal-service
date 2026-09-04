package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.CalendarSourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A calendar event, either created directly by a user ({@link CalendarSourceType#USER}) or the
 * single shared event auto-created for a {@link Paper} ({@link CalendarSourceType#PAPER}).
 *
 * <p>There is exactly one {@code PAPER}-type event per paper (enforced by the unique constraint
 * on {@link #sourcePaper}), created the moment the paper is created. Visibility for that one
 * event is a per-relationship rule handled entirely in {@code CalendarEventService}, not stored
 * here: instructors see every {@code PAPER} event as a blanket role permission (no row needed),
 * while students and admins see it only once a {@link CalendarEventAttendee} row attaches them.
 */
@Getter
@Setter
@Entity
@Table(name = "calendar_events")
public class CalendarEvent extends AuditableEntity {

    /**
     * The creating user, for {@code USER} events only — null for {@code PAPER} events, which
     * have no single owner and are never user-editable.
     */
    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * For an all-day event, only the date portion is meaningful.
     */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    /**
     * A preset palette key (e.g. {@code "blue"}), not free-form hex. Fixed to one constant
     * value for {@code PAPER} events — never user-choosable.
     */
    @Column(nullable = false, length = 20)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private CalendarSourceType sourceType;

    /**
     * Set only for {@code PAPER} events. The unique constraint is what guarantees "one shared
     * event per paper" at the database level.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_paper_id")
    private Paper sourcePaper;

    /**
     * Only meaningful for {@code CLASS} events: {@code true} means visible to every student
     * (a live role-based rule, no attendee rows), {@code false} means scoped to the classes in
     * {@link CalendarEventClass} (visibility via attendee rows instead). Named without the
     * {@code is} prefix on the field itself to keep the Lombok-generated {@code isGlobal()}/
     * {@code setGlobal()} accessor pair unambiguous.
     */
    @Column(name = "is_global", nullable = false)
    private boolean global;

    /**
     * The meeting link for a scheduled class (e.g. a Zoom URL), shown prominently and clickable
     * in the event details. Optional, and only ever set on {@code CLASS} events today — named
     * {@code classLink} rather than {@code link} to avoid clashing with the unrelated
     * event-to-class association ({@link CalendarEventClass}) already called "link" throughout
     * this codebase.
     */
    @Column(name = "class_link", length = 2048)
    private String classLink;

}
