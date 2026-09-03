package dopaminelite.payment_portal.dto.calendar;

import dopaminelite.payment_portal.entity.enums.CalendarSourceType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a calendar event. {@link #paperId}/{@link #paperTitle}/
 * {@link #linkedPortalNames} are only populated for {@code PAPER}-sourced events, and are
 * denormalized here so the frontend never needs a second fetch to show them.
 */
@Data
public class CalendarEventResponse {

    private UUID id;

    private UUID ownerId;

    private String title;

    private String description;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private boolean allDay;

    private String color;

    private CalendarSourceType sourceType;

    private UUID paperId;

    private String paperTitle;

    private List<String> linkedPortalNames;

    /** Only meaningful for {@code CLASS} events. */
    private boolean global;

    /** Only populated for non-global {@code CLASS} events. */
    private List<ClassRefDto> classes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
