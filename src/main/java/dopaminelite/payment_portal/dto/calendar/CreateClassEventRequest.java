package dopaminelite.payment_portal.dto.calendar;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request to schedule a class as a calendar event. Exactly one of {@code global=true} or a
 * non-empty {@code classes} is expected — validated in {@code CalendarEventService}, since it's
 * a cross-field business rule, not a per-field annotation.
 *
 * <p>{@code studentIds} is the BFF-resolved, de-duplicated member list for the selected
 * classes (empty/ignored when {@code global}) — this backend never resolves class membership
 * itself, it just attaches whatever UUIDs it's handed.
 */
@Data
public class CreateClassEventRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    /** The class's meeting link (e.g. Zoom), shown prominently and clickable in event details. */
    private String classLink;

    @NotNull(message = "Start time is required")
    private LocalDateTime startAt;

    @NotNull(message = "End time is required")
    private LocalDateTime endAt;

    private boolean allDay;

    @NotBlank(message = "Color is required")
    private String color;

    private boolean global;

    @Valid
    private List<ClassRefDto> classes;

    private List<UUID> studentIds;

}
