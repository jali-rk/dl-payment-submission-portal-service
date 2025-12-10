package dopaminelite.payment_portal.exception;

public class ValidationException extends RuntimeException {
    
    public ValidationException(String message) {
        super(message);
    }
    
    public static ValidationException rejectionReasonRequired() {
        return new ValidationException("Rejection reason is required when status is REJECTED");
    }
    
    public static ValidationException portalNameMismatch(String expected, String actual) {
        return new ValidationException(
            String.format("Portal name confirmation mismatch. Expected: '%s', Got: '%s'", expected, actual)
        );
    }
    
}
