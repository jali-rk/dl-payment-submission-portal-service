package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An instructor-entered mark record for one student on one {@link Paper}.
 *
 * <p>Deliberately decoupled from {@link PaperSlot}/{@link PaymentSubmission} — a mark only
 * requires the paper and a student verified to exist (by code number, against the BFF), not
 * an approved payment or an existing slot. Section marks ({@link #mcqMarks}/
 * {@link #structuredMarks}/{@link #essayMarks}) and {@link #totalMarks} are all entered
 * independently by the instructor and are never cross-checked or auto-computed against each
 * other.
 */
@Getter
@Setter
@Entity
@Table(name = "paper_marks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"paper_id", "student_id"})
})
public class PaperMark extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id", nullable = false)
    private Paper paper;

    /**
     * The student's ID as resolved from the BFF's student-by-code-number lookup at entry time.
     */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Embedded
    private MarkStudentSnapshot studentSnapshot;

    /**
     * Mark for the paper's MCQ section, or null if the paper has no MCQ section enabled.
     */
    // precision=9, matching Paper.mcqMaxMarks's raised ceiling - a recorded score can be as
    // large as its paper's configured max. totalMarks below is deliberately left at 6/100:
    // it's a separate, always-out-of-100 figure, not tied to the section maximums.
    @Column(name = "mcq_marks", precision = 9, scale = 3)
    private BigDecimal mcqMarks;

    /**
     * Mark for the paper's Structured section, or null if not enabled on the paper.
     */
    @Column(name = "structured_marks", precision = 9, scale = 3)
    private BigDecimal structuredMarks;

    /**
     * Mark for the paper's Essay section, or null if not enabled on the paper.
     */
    @Column(name = "essay_marks", precision = 9, scale = 3)
    private BigDecimal essayMarks;

    /**
     * The student's overall mark out of 100, entered independently by the instructor — not
     * computed as the sum of the section marks above.
     */
    @Column(name = "total_marks", nullable = false, precision = 6, scale = 3)
    private BigDecimal totalMarks;

    /**
     * UUID of the instructor who first entered this mark. Set once, never changed.
     */
    @Column(name = "entered_by_instructor_id", nullable = false, updatable = false)
    private UUID enteredByInstructorId;

    /**
     * UUID of the instructor who most recently edited this mark.
     */
    @Column(name = "last_updated_by_instructor_id", nullable = false)
    private UUID lastUpdatedByInstructorId;

    /**
     * This student's rank on the paper's leaderboard as of the last "Generate Ranks" run, or
     * null if ranks have never been generated for this paper (or this mark was added after the
     * most recent generation). Standard competition ranking — tied {@link #totalMarks} share a
     * rank, the next distinct value skips accordingly. Never touched by create/update/delete —
     * only the explicit generate operation writes this, which is what lets the leaderboard go
     * stale between generations.
     *
     * <p>Mapped to column {@code leaderboard_rank} rather than {@code rank} since {@code RANK}
     * has special meaning in SQL (a window function), sidestepping any reserved-word friction.
     */
    @Column(name = "leaderboard_rank")
    private Integer rank;

}
