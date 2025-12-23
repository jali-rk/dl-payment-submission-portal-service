package dopaminelite.payment_portal.entity.enums;

/**
 * Enumeration defining the approval status of a payment submission.
 */
public enum SubmissionStatus {
    /**
     * Submission is awaiting review by an administrator.
     */
    PENDING,
    
    /**
     * Submission has been approved by an administrator.
     */
    APPROVED,
    
    /**
     * Submission has been rejected by an administrator.
     */
    REJECTED
}
