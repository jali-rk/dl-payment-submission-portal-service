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

}
