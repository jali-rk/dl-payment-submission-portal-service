package dopaminelite.payment_portal.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response structure for API error handling.
 * Provides consistent error information across all endpoints.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    /**
     * HTTP status code of the error.
     */
    private int status;
    
    /**
     * Application-specific error code for client handling.
     */
    private String code;
    
    /**
     * Human-readable error message.
     */
    private String message;
    
    /**
     * Additional error details (e.g., validation errors, field-specific messages).
     */
    private Map<String, Object> details;
    
    /**
     * Timestamp when the error occurred.
     */
    private LocalDateTime timestamp;
    
    public ErrorResponse(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponse(int status, String code, String message, Map<String, Object> details) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }
    
}
