package dopaminelite.payment_portal.dto.calendar;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for one attendee on a paper's shared calendar event (admin-added, or a
 * system-attached student). Backs the Papers admin "Manage Attendees" picker.
 */
@Data
public class CalendarEventAttendeeDto {

    private UUID id;

    private UUID userId;

    private String email;

    private String fullName;

    private String role;

    private UUID addedByUserId;

    private LocalDateTime addedAt;

}
