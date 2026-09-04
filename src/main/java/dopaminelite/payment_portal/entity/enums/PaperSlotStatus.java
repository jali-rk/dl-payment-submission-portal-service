package dopaminelite.payment_portal.entity.enums;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Computed (never persisted) lifecycle status of a {@code PaperSlot}.
 * The status is always derived from the owning paper's validity window and the
 * slot's {@code consumedAt} timestamp, so it can never drift out of sync with
 * either of those — editing a paper's dates instantly changes the effective
 * status of every unconsumed slot, and a consumed slot can never revert.
 */
public enum PaperSlotStatus {

    /**
     * The paper's start date hasn't arrived yet.
     */
    SCHEDULED,

    /**
     * The paper is within its validity window and the slot has not been consumed.
     */
    AVAILABLE,

    /**
     * The paper's end date has passed and the slot was never consumed.
     */
    EXPIRED,

    /**
     * The slot has already been redeemed by an instructor. Terminal state.
     */
    CONSUMED;

    /**
     * Computes the effective status of a slot as a pure function of the current date,
     * the owning paper's validity window, and whether the slot has been consumed.
     *
     * @param today the current date
     * @param startDate the paper's start date
     * @param endDate the paper's end date
     * @param consumedAt when the slot was consumed, or null if not yet consumed
     * @return the effective status
     */
    public static PaperSlotStatus compute(LocalDate today, LocalDate startDate, LocalDate endDate, LocalDateTime consumedAt) {
        if (consumedAt != null) {
            return CONSUMED;
        }
        if (today.isBefore(startDate)) {
            return SCHEDULED;
        }
        if (today.isAfter(endDate)) {
            return EXPIRED;
        }
        return AVAILABLE;
    }
}
