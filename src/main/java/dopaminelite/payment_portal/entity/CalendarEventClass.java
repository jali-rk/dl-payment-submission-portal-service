package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Links a {@code CLASS}-sourced {@link CalendarEvent} to one class it was scheduled for. The
 * class itself lives entirely in the separate `videoms` service — this only stores the
 * {@code classId} (opaque string, not assumed to be a UUID — that service's ID format isn't
 * something this backend controls) plus a display-name snapshot, both resolved by the BFF.
 * Empty for {@code isGlobal} events.
 */
@Getter
@Setter
@Entity
@Table(name = "calendar_event_classes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "class_id"})
})
public class CalendarEventClass extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private CalendarEvent event;

    @Column(name = "class_id", nullable = false)
    private String classId;

    @Column(name = "class_name")
    private String className;

}
