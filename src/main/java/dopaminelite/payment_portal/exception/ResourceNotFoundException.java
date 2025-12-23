package dopaminelite.payment_portal.exception;

import java.util.UUID;

/**
 * Exception thrown when a requested resource cannot be found in the system.
 * Typically results in a 404 HTTP status code.
 */
public class ResourceNotFoundException extends RuntimeException {
    
    /**
     * Constructs a new ResourceNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Factory method for creating a portal not found exception by ID.
     *
     * @param id the UUID of the portal that was not found
     * @return a new ResourceNotFoundException with appropriate message
     */
    public static ResourceNotFoundException portalNotFound(UUID id) {
        return new ResourceNotFoundException("Payment portal not found with id: " + id);
    }
    
    /**
     * Factory method for creating a portal not found exception by name.
     *
     * @param name the name of the portal that was not found
     * @return a new ResourceNotFoundException with appropriate message
     */
    public static ResourceNotFoundException portalNotFound(String name) {
        return new ResourceNotFoundException("Payment portal not found with name: " + name);
    }
    
}
