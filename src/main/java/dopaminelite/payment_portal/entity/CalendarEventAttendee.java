package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Attaches one user to a {@link CalendarEvent} so it shows up on their calendar.
 *
 * <p>Used two ways: a student is attached (with no {@link #addedByUserId}, i.e. system-attached)
 * the moment their approved payment creates a paper slot; an admin/main_admin is attached
 * explicitly by a main_admin via the Papers admin "Manage Attendees" picker (which does set
 * {@link #addedByUserId}). Instructors are deliberately never represented here — see
 * {@code CalendarEventService} for why.
 */
@Getter
@Setter
@Entity
@Table(name = "calendar_event_attendees", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "user_id"})
})
public class CalendarEventAttendee extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private CalendarEvent event;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Display snapshot captured at the time this attendee was added, so the Papers admin UI
     * doesn't need to re-resolve user details from User Service on every read. Null for
     * system-attached students (their name/email is already visible elsewhere in the admin UI).
     */
    private String email;

    @Column(name = "full_name")
    private String fullName;

    private String role;

    /**
     * Null for a system-attached student (their calendar visibility is earned automatically,
     * not granted by an admin). Set to the acting main_admin's ID otherwise.
     */
    @Column(name = "added_by_user_id")
    private UUID addedByUserId;

}
