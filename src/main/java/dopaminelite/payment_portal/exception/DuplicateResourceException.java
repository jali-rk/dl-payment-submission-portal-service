package dopaminelite.payment_portal.exception;

/**
 * Exception thrown when attempting to create a resource that already exists.
 * Typically results in a 409 Conflict HTTP status code.
 */
public class DuplicateResourceException extends RuntimeException {
    
    /**
     * Constructs a new DuplicateResourceException with the specified message.
     *
     * @param message the detail message
     */
    public DuplicateResourceException(String message) {
        super(message);
    }
    
    /**
     * Factory method for creating an exception when a portal name already exists.
     *
     * @param name the duplicate portal name
     * @return a new DuplicateResourceException with appropriate message
     */
    public static DuplicateResourceException portalNameExists(String name) {
        return new DuplicateResourceException("Payment portal with name '" + name + "' already exists");
    }
    
}
