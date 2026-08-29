package dopaminelite.payment_portal.dto.paper;

/**
 * Query-parameter filter for listing papers by their validity window relative to today.
 * Distinct from {@code PaperSlotStatus} — a paper has no notion of "consumed".
 */
public enum PaperWindowFilter {
    UPCOMING,
    ACTIVE,
    PAST
}
