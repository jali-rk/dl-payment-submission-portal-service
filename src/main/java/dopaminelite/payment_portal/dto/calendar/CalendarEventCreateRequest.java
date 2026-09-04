package dopaminelite.payment_portal.dto.calendar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request DTO for creating a {@code USER}-sourced calendar event. There is no way to create a
 * {@code PAPER}-sourced event through this API — those are only ever created internally when a
 * paper is created.
 */
@Data
public class CalendarEventCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    private String description;

    @NotNull(message = "Start time is required")
    private LocalDateTime startAt;

    @NotNull(message = "End time is required")
    private LocalDateTime endAt;

    private boolean allDay;

    @NotBlank(message = "Color is required")
    private String color;

}
