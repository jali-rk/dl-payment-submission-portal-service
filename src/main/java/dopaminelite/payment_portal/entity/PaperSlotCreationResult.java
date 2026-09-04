package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.PaperSlotCreationOutcome;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Durable, queryable record of what happened when the system attempted to create a
 * {@link PaperSlot} for a (paper, submission) pair. This is the audit trail an admin
 * views to see why a slot succeeded or failed — a {@code FAILED} outcome leaves no
 * {@link PaperSlot} row, so this table is the only place that failure is ever recorded.
 *
 * <p>Each (payment_submission_id, paper_id) pair has exactly one current row, upserted in
 * place whenever slot creation is (re)attempted for that pair.
 */
@Getter
@Setter
@Entity
@Table(name = "paper_slot_creation_results", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"payment_submission_id", "paper_id"})
})
public class PaperSlotCreationResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_submission_id", nullable = false)
    private PaymentSubmission paymentSubmission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    /**
     * Snapshot of the paper's title at the time of the attempt, so this record stays
     * meaningful even if the paper is later renamed.
     */
    @Column(name = "paper_title_snapshot", nullable = false)
    private String paperTitleSnapshot;

    /**
     * The slot that resulted from this attempt, if one exists (outcome CREATED or
     * ALREADY_EXISTS). Null when the outcome is FAILED.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_slot_id")
    private PaperSlot paperSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaperSlotCreationOutcome outcome;

    /**
     * Human-readable explanation, populated when outcome is FAILED. The underlying exception
     * is logged server-side and never stored here.
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

}
