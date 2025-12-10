package dopaminelite.payment_portal.exception;

public class DuplicateResourceException extends RuntimeException {
    
    public DuplicateResourceException(String message) {
        super(message);
    }
    
    public static DuplicateResourceException portalNameExists(String name) {
        return new DuplicateResourceException("Payment portal with name '" + name + "' already exists");
    }
    
}
