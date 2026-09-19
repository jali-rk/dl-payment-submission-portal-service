package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
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
    /**
     * Overrides the end of this slot's usable window, or null to use the paper-event's own
     * {@code endDate} — which is the case for every slot that isn't part of a correlation, so
     * nothing needed backfilling when this column arrived.
     *
     * <p>Set when a later payment entitles the student to a longer window for the same paper. A
     * student who paid in September gets the early-access window (say to the 7th); when their
     * October payment is approved they do not get a second slot, this one is extended to the 9th.
     * It only ever moves later — see {@code PaperSlotWriter.extendCorrelatedSiblingIfAny}.
     */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    /**
     * The correlation this slot's paper-event belonged to at creation, or null if it had none.
     *
     * <p>Denormalised from {@code paper.correlation} together with {@link #studentId} purely so
     * that "does this student already hold a slot for this paper?" can be both answered and
     * <em>enforced</em> without a join — the partial unique index over the pair is what stops two
     * concurrent approvals, one per portal, from each creating a slot. Never updated: a
     * paper-event can't join or leave a correlation once it has slots.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "correlation_id")
    private PaperCorrelation correlation;

    /**
     * The owning student, copied from the payment submission. See {@link #correlation}.
     */
    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    /**
     * UUID of the instructor who consumed this slot.
     */
    @Column(name = "consumed_by_instructor_id")
    private UUID consumedByInstructorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * The last day this slot can be scanned: its own {@link #validUntil} when one has been set by
     * an extension, otherwise the paper-event's end date. Use this rather than reading either
     * field directly, so the two never disagree.
     */
    public LocalDate effectiveEndDate() {
        return validUntil != null ? validUntil : paper.getEndDate();
    }

    @PrePersist
    protected void onCreate() {
        if (qrToken == null) {
            qrToken = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Colombo"));
    }

}
