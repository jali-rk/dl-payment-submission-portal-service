package dopaminelite.payment_portal.entity.enums;

/**
 * What created a {@code CalendarEvent} and, in turn, who's allowed to edit/delete it.
 */
public enum CalendarSourceType {

    /**
     * Created directly by a user through the calendar API. Editable/deletable only by its owner.
     */
    USER,

    /**
     * The single shared event auto-created for a {@code Paper} at the moment it's created.
     * Never editable or deletable through the calendar API — its dates are kept in sync with
     * the paper automatically, and who sees it is governed entirely by attendee rows plus the
     * blanket instructor visibility rule (see {@code CalendarEventService}).
     */
    PAPER,

    /**
     * A "scheduled class" explicitly created by a MAIN_ADMIN, either global (every student,
     * live role-based visibility, no stored rows) or scoped to one or more classes (visibility
     * via attendee rows, continuously kept in sync as class enrollment changes). Unlike
     * {@code USER} events, editable/deletable by any MAIN_ADMIN, not just its creator.
     */
    CLASS

}
