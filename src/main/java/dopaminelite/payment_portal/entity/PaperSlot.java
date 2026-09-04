package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Entity representing a redeemable, QR-coded slot granted to a student for a {@link Paper}
 * after their {@link PaymentSubmission} to a linked portal is approved.
 *
 * <p>The slot's lifecycle status (scheduled/available/expired) is intentionally not stored
 * here — it is always computed from the owning paper's validity window plus {@link #consumedAt}
 * (see {@code PaperSlotStatus.compute}). Only the one genuinely stateful, irreversible
 * transition — being consumed — is persisted.
 */
@Getter
@Setter
@Entity
@Table(name = "paper_slots", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"qr_token"}),
    @UniqueConstraint(columnNames = {"paper_id", "payment_submission_id"})
})
public class PaperSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    /**
     * The approved submission this slot was granted for. Supplies student and payment
     * details at read time — deliberately not duplicated onto this entity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_submission_id", nullable = false)
    private PaymentSubmission paymentSubmission;

    /**
     * Opaque token embedded in the slot's QR code. Kept separate from {@link #getId()} so the
     * externally-facing code isn't the internal primary key.
     */
    @Column(name = "qr_token", nullable = false, unique = true, updatable = false)
    private UUID qrToken;

    /**
     * When the slot was consumed (attendance marked) by an instructor. Null while unconsumed.
     */
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    /**
     * UUID of the instructor who consumed this slot.
     */
    @Column(name = "consumed_by_instructor_id")
    private UUID consumedByInstructorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (qrToken == null) {
            qrToken = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Colombo"));
    }

}
