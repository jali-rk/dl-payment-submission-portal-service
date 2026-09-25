package dopaminelite.payment_portal.exception;

/**
 * Exception thrown when the caller is authenticated but not allowed to perform the action —
 * distinct from {@link ResourceNotFoundException}: the resource exists, the caller just isn't
 * the one allowed to change it. Results in a 403 Forbidden HTTP status code.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    /**
     * Factory method for an instructor trying to edit or delete a mark another instructor
     * entered.
     *
     * @param markId the mark's ID
     * @return a new ForbiddenException with an appropriate message
     */
    public static ForbiddenException notYourMark(java.util.UUID markId) {
        return new ForbiddenException(
            "Mark " + markId + " was entered by another instructor and cannot be edited or deleted here"
        );
    }

}
