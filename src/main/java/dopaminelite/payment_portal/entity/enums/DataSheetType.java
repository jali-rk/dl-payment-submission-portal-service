package dopaminelite.payment_portal.entity.enums;

/**
 * Enumeration defining the types of data sheets that can be exported.
 * Used to filter submissions when generating export reports.
 */
public enum DataSheetType {
    /**
     * Export only approved submissions.
     */
    APPROVED,
    
    /**
     * Export only rejected submissions.
     */
    REJECTED,
    
    /**
     * Export only pending submissions.
     */
    PENDING,
    
    /**
     * Export all submissions regardless of status.
     */
    ALL
}
