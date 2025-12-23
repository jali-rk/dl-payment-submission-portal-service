package dopaminelite.payment_portal.exception;

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
    
}
