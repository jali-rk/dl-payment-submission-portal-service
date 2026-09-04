package dopaminelite.payment_portal.exception;

import java.util.UUID;

/**
 * Exception thrown when business validation rules are violated.
 * Typically results in a 400 Bad Request HTTP status code.
 */
public class ValidationException extends RuntimeException {
    
    /**
     * Constructs a new ValidationException with the specified message.
     *
     * @param message the detail message
     */
    public ValidationException(String message) {
        super(message);
    }
    
    /**
     * Factory method for when rejection reason is required but not provided.
     *
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException rejectionReasonRequired() {
        return new ValidationException("Rejection reason is required when status is REJECTED");
    }
    
    /**
     * Factory method for when portal name confirmation doesn't match.
     *
     * @param expected the expected portal name
     * @param actual the actual portal name provided
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException portalNameMismatch(String expected, String actual) {
        return new ValidationException(
            String.format("Portal name confirmation mismatch. Expected: '%s', Got: '%s'", expected, actual)
        );
    }

    /**
     * Factory method for when a mark scheme has no section enabled.
     *
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException markSchemeRequiresAtLeastOneSection() {
        return new ValidationException(
            "At least one of mcqMaxMarks, structuredMaxMarks, or essayMaxMarks must be set"
        );
    }

    /**
     * Factory method for when a paper's mark scheme can't be changed because marks already exist.
     *
     * @param paperId the paper whose scheme change was rejected
     * @param existingMarkCount how many marks currently exist for the paper
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException markSchemeLocked(UUID paperId, long existingMarkCount) {
        return new ValidationException(String.format(
            "Cannot change the mark scheme for paper %s: %d mark(s) already exist. Delete them first.",
            paperId, existingMarkCount
        ));
    }

    /**
     * Factory method for when a paper can't be deleted because slots and/or marks already
     * exist for it.
     *
     * @param paperId the paper whose deletion was rejected
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException paperHasDependents(UUID paperId) {
        return new ValidationException(String.format(
            "Cannot delete paper %s: it has existing paper slots and/or marks. Remove them first.",
            paperId
        ));
    }

    /**
     * Factory method for when a PATCH/DELETE is attempted on a {@code PAPER}-sourced calendar
     * event, which is always backend-managed and never user-editable.
     *
     * @param eventId the event whose edit/delete was rejected
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException calendarEventNotUserManaged(UUID eventId) {
        return new ValidationException(String.format(
            "Calendar event %s is system-managed and cannot be edited or deleted directly",
            eventId
        ));
    }

    /**
     * Factory method for when a caller attempts to edit/delete a {@code USER} calendar event
     * they don't own.
     *
     * @param eventId the event whose edit/delete was rejected
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException calendarEventNotOwnedByCaller(UUID eventId) {
        return new ValidationException(String.format(
            "Calendar event %s does not belong to the caller",
            eventId
        ));
    }

    /**
     * Factory method for when a caller who isn't a MAIN_ADMIN attempts to edit/delete a
     * {@code CLASS}-sourced calendar event.
     *
     * @param eventId the event whose edit/delete was rejected
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException calendarEventNotManageableByCaller(UUID eventId) {
        return new ValidationException(String.format(
            "Calendar event %s can only be edited or deleted by a main admin",
            eventId
        ));
    }

    /**
     * Factory method for a scheduled-class event request that's neither global nor scoped to
     * any class, or that's both at once.
     *
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException classEventAudienceInvalid() {
        return new ValidationException(
            "A scheduled class event must be either global, or scoped to at least one class — not both, not neither"
        );
    }

    /**
     * Factory method for when a calendar event's color isn't one of the fixed preset palette
     * values.
     *
     * @param color the rejected color value
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException invalidCalendarEventColor(String color) {
        return new ValidationException("Invalid calendar event color: " + color);
    }

    /**
     * Factory method for when a scheduled class's meeting link isn't a well-formed http(s) URL.
     *
     * @param classLink the rejected value
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException invalidCalendarEventClassLink(String classLink) {
        return new ValidationException("Class link must be a valid http(s) URL: " + classLink);
    }

    /**
     * Factory method for when "Generate Ranks" is pressed on a paper with no marks recorded.
     *
     * @param paperId the paper whose rank generation was rejected
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException noMarksToGenerateRanksFor(UUID paperId) {
        return new ValidationException(
            "Cannot generate ranks for paper " + paperId + ": no marks have been recorded yet"
        );
    }

    /**
     * Factory method for when a MAIN_ADMIN attempts to publish a paper's leaderboard before
     * ranks have ever been generated for it.
     *
     * @param paperId the paper whose publish attempt was rejected
     * @return a new ValidationException with appropriate message
     */
    public static ValidationException leaderboardNotGeneratedYet(UUID paperId) {
        return new ValidationException(
            "Cannot publish the leaderboard for paper " + paperId + ": ranks have not been generated yet"
        );
    }

}
