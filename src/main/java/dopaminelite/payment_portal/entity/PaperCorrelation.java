package dopaminelite.payment_portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Groups the paper-events that are really the same exam paper.
 *
 * <p>A paper sat in the first week of a month is created twice: once with an early-access window
 * linked to last month's payment portal (say Oct 5-7, for students who already paid in September),
 * and once with the full window linked to this month's portal (Oct 5-9, for students who are only
 * now paying). They are two {@link Paper} rows because they have different windows and different
 * portals — but they are one paper. Without something tying them together a student who paid both
 * months received a slot from each, so they held two QR codes for one paper and could be scanned
 * twice, and their marks and leaderboard were split across the two rows.
 *
 * <p>So this owns everything that belongs to the paper rather than to a sitting of it: the mark
 * scheme, the marks and the leaderboard (see {@link MarkOwner}). The paper-events keep what is
 * genuinely theirs — dates, linked portals, slots, calendar event.
 *
 * <p>Entirely opt-in. A paper-event with no correlation is untouched by any of this, which is what
 * lets the feature ship without migrating a single existing paper, mark or leaderboard.
 */
@Getter
@Setter
@Entity
@Table(name = "paper_correlations")
public class PaperCorrelation extends AuditableEntity implements MarkOwner {

    /**
     * Admin-facing identifier, typed when creating the correlation and used to pick it when
     * creating a paper-event — e.g. {@code OCT-W1}. Uppercase, no spaces, so it can't fail to
     * match through a stray case or space difference.
     *
     * <p>Note that nothing matches <em>on</em> this string: paper-events point at the correlation
     * by id. It exists to be recognised by a human, which is why renaming it is always safe.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /**
     * What instructors and students see wherever this paper is named. Separate from {@link #code}
     * because a code reads as a code — "OCT-W1" is fine for an admin picking from a list, and
     * poor as the title of a paper a student is about to sit.
     */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    /**
     * Maximum mark for the MCQ section, or null if this paper has no MCQ section. Set here rather
     * than on each paper-event so the two sittings cannot disagree about what the paper is marked
     * out of — the inconsistency is impossible rather than merely validated against. Immutable
     * once any mark exists, the same rule paper-events have always had.
     */
    @Column(name = "mcq_max_marks", precision = 9, scale = 3)
    private BigDecimal mcqMaxMarks;

    /**
     * Maximum mark for the Structured section, or null if absent. See {@link #mcqMaxMarks}.
     */
    @Column(name = "structured_max_marks", precision = 9, scale = 3)
    private BigDecimal structuredMaxMarks;

    /**
     * Maximum mark for the Essay section, or null if absent. See {@link #mcqMaxMarks}.
     */
    @Column(name = "essay_max_marks", precision = 9, scale = 3)
    private BigDecimal essayMaxMarks;

    /**
     * Whether this paper's leaderboard is currently visible to students. Named without an "is"
     * prefix to keep the Lombok accessors unambiguous, matching {@code Paper.leaderboardPublished}.
     */
    @Column(name = "leaderboard_published", nullable = false)
    private boolean leaderboardPublished = false;

    @Column(name = "leaderboard_last_generated_at")
    private LocalDateTime leaderboardLastGeneratedAt;

    @Column(name = "leaderboard_last_generated_by")
    private UUID leaderboardLastGeneratedBy;

    @Override
    public String getDisplayTitle() {
        return displayName;
    }

}
