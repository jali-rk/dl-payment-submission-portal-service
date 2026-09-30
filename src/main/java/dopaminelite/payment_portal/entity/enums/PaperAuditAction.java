package dopaminelite.payment_portal.entity.enums;

/**
 * The paper-event decisions worth keeping a record of — the ones that change who gets a QR code
 * for what, or how it is graded. Deliberately not every field: a corrected typo in a title is
 * noise, whereas a re-pointed portal or a changed window quietly changes who is entitled to sit
 * the paper.
 */
public enum PaperAuditAction {

    PAPER_CREATED,

    /** Grouped with another sitting of the same paper — from here, the two share marks and QRs. */
    CORRELATION_ATTACHED,

    CORRELATION_DETACHED,

    /** Which payments entitle a student to this paper. */
    PORTALS_CHANGED,

    /** The window students can sit it in. */
    DATES_CHANGED,

    MARK_SCHEME_CHANGED,

    PAPER_DELETED

}
