package dopaminelite.payment_portal.dto.calendar;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Backs the enrollment-sync hook: "these students now have access to this class, attach them
 * to every calendar event linked to it (idempotently)." Sent by the BFF as a side effect of an
 * existing add-to-class action — see {@code CalendarEventService.syncClassEnrollmentToEvents}.
 */
@Data
public class SyncClassAttendeesRequest {

    private List<UUID> studentIds;

}
