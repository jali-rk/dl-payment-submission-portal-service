package dopaminelite.payment_portal.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Whatever a {@link PaperMark} is recorded against — the gradable thing, as opposed to the
 * window during which it can be sat.
 *
 * <p>Usually that is a {@link Paper} itself. But one real exam paper is sometimes created as two
 * paper-events with different windows linked to different payment portals (an early-access window
 * for students who paid last month, a full-week window for students who paid this month). Those
 * two are the same paper to everyone who matters — the instructor marking it, the student sitting
 * it, the leaderboard ranking it — so when they are grouped under a {@link PaperCorrelation} it is
 * the correlation, not either paper-event, that owns the mark scheme, the marks and the
 * leaderboard.
 *
 * <p>A paper-event with no correlation owns its own marks exactly as it always has; nothing about
 * existing papers changes. {@code MarkOwnerResolver} decides which of the two is in play, and
 * everything downstream works against this interface without caring.
 */
public interface MarkOwner {

    UUID getId();

    /**
     * The name instructors and students should see for this paper. For a correlation that is its
     * display name, deliberately in place of either paper-event's own title — a student who only
     * paid last month must not be shown "Oct 5-9" when their own access ends on the 7th, and two
     * students holding the same paper must not see two different names for it.
     */
    String getDisplayTitle();

    BigDecimal getMcqMaxMarks();

    void setMcqMaxMarks(BigDecimal mcqMaxMarks);

    BigDecimal getStructuredMaxMarks();

    void setStructuredMaxMarks(BigDecimal structuredMaxMarks);

    BigDecimal getEssayMaxMarks();

    void setEssayMaxMarks(BigDecimal essayMaxMarks);

    boolean isLeaderboardPublished();

    void setLeaderboardPublished(boolean leaderboardPublished);

    LocalDateTime getLeaderboardLastGeneratedAt();

    void setLeaderboardLastGeneratedAt(LocalDateTime leaderboardLastGeneratedAt);

    UUID getLeaderboardLastGeneratedBy();

    void setLeaderboardLastGeneratedBy(UUID leaderboardLastGeneratedBy);

}
