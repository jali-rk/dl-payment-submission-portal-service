package dopaminelite.payment_portal.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public static ResourceNotFoundException portalNotFound(UUID id) {
        return new ResourceNotFoundException("Payment portal not found with id: " + id);
    }
    
    public static ResourceNotFoundException portalNotFound(String name) {
        return new ResourceNotFoundException("Payment portal not found with name: " + name);
    }
    
}
