package dopaminelite.payment_portal.dto.calendar;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Partial-update request for a {@code USER} event — only non-null fields are applied. There is
 * no {@code allDay} setter here deliberately paired with dates the way create's is: send both
 * {@code startAt}/{@code endAt} and {@code allDay} together when toggling it, since a
 * half-updated all-day/timed state would be ambiguous.
 */
@Data
public class CalendarEventUpdateRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    /** Only meaningful for {@code CLASS} events; ignored otherwise. */
    private String classLink;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Boolean allDay;

    private String color;

}
