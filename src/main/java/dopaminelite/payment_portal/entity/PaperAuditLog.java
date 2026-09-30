package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.PaperAuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * An append-only record of the decisions made about a paper-event.
 *
 * <p>Which portal a paper-event is linked to, what window it runs for, and which correlation it
 * belongs to together decide who gets a QR code for what. Those are hand-made decisions, taken
 * months apart, and without a trail a student turning up holding an unexpected code — or two —
 * can only be explained by guesswork.
 *
 * <p>Ids are stored bare rather than as foreign keys: the trail has to outlive the paper-event or
 * correlation it describes, and a deletion is precisely the event you most want a record of.
 */
@Getter
@Setter
@Entity
@Table(name = "paper_audit_log")
public class PaperAuditLog extends BaseEntity {

    @Column(name = "paper_id", nullable = false, updatable = false)
    private UUID paperId;

    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private PaperAuditAction action;

    /** Compact "old -> new" summary, written for someone reading the trail later. */
    @Column(length = 2000, updatable = false)
    private String details;

    /** The admin who made the change, or null when the request carried no usable token. */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Colombo"));
    }

}
