package dopaminelite.payment_portal.dto.calendar;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request to add an admin/main_admin as an attendee on a paper's shared calendar event. The
 * BFF resolves {@code userId}/{@code email}/{@code fullName}/{@code role} from its User Service
 * email-search and passes them through here as a display snapshot — this service never calls
 * out to User Service itself.
 */
@Data
public class CalendarEventAttendeeAddRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    private String email;

    private String fullName;

    private String role;

}
