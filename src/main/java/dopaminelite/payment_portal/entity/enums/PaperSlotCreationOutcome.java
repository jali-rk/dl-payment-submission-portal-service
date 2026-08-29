package dopaminelite.payment_portal.entity.enums;

/**
 * Outcome of an attempt to create a {@code PaperSlot} for a (paper, submission) pair.
 */
public enum PaperSlotCreationOutcome {

    /**
     * A new slot was created by this attempt.
     */
    CREATED,

    /**
     * A slot for this paper and submission already existed; nothing was created.
     */
    ALREADY_EXISTS,

    /**
     * Slot creation was attempted but failed. See the associated message for details.
     */
    FAILED
}
