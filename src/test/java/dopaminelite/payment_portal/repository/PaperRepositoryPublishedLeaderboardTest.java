package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.config.JpaConfig;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperCorrelation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The list a student sees of papers whose leaderboard is published. Which flag counts depends on
 * who owns the grading: a standalone paper's own, or the correlation's for a paper-event in one.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
@DisplayName("Published leaderboard list")
class PaperRepositoryPublishedLeaderboardTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PaperRepository paperRepository;

    private Paper standalone(String title, boolean published) {
        Paper paper = new Paper();
        paper.setTitle(title);
        paper.setStartDate(LocalDate.now().minusDays(5));
        paper.setEndDate(LocalDate.now().minusDays(3));
        paper.setLeaderboardPublished(published);
        return entityManager.persistAndFlush(paper);
    }

    private PaperCorrelation correlation(String code, boolean published) {
        PaperCorrelation correlation = new PaperCorrelation();
        correlation.setCode(code);
        correlation.setDisplayName(code + " paper");
        correlation.setLeaderboardPublished(published);
        return entityManager.persistAndFlush(correlation);
    }

    private Paper sitting(String title, PaperCorrelation correlation) {
        Paper paper = new Paper();
        paper.setTitle(title);
        paper.setStartDate(LocalDate.now().minusDays(5));
        paper.setEndDate(LocalDate.now().minusDays(3));
        paper.setCorrelation(correlation);
        return entityManager.persistAndFlush(paper);
    }

    @Test
    @DisplayName("lists published standalone papers, one entry per published correlation, and nothing hidden")
    void listsEachRealPaperOnceWhenPublished() {
        Paper publishedStandalone = standalone("Published standalone", true);
        standalone("Hidden standalone", false);

        PaperCorrelation published = correlation("OCT-W1", true);
        Paper firstSitting = sitting("OCT-W1 early access", published);
        sitting("OCT-W1 full week", published);

        PaperCorrelation hidden = correlation("OCT-W2", false);
        sitting("OCT-W2 early access", hidden);
        sitting("OCT-W2 full week", hidden);

        entityManager.clear();

        var page = paperRepository.findWithPublishedLeaderboard(PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Paper::getId)
                .containsExactlyInAnyOrder(publishedStandalone.getId(), firstSitting.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("two paper-events created in the same instant still yield one entry for their correlation")
    void tieOnCreationTimeStillYieldsOneEntry() {
        PaperCorrelation published = correlation("OCT-W1", true);
        sitting("Early access", published);
        sitting("Full week", published);
        // Not reachable through the app, where each paper-event is its own request, but a script or
        // import could do it - and then both would count as "created first" and list twice.
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE papers SET created_at = TIMESTAMP '2026-10-01 09:00:00' "
                        + "WHERE correlation_id = :id")
                .setParameter("id", published.getId())
                .executeUpdate();
        entityManager.clear();

        var page = paperRepository.findWithPublishedLeaderboard(PageRequest.of(0, 50));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("the date span of a correlation runs from its earliest start to its latest end")
    void dateSpanCoversEveryPaperEventInTheCorrelation() {
        PaperCorrelation correlation = correlation("OCT-W1", true);
        PaperCorrelation other = correlation("OCT-W2", true);

        Paper earlyAccess = sitting("Early access", correlation);
        earlyAccess.setStartDate(LocalDate.of(2026, 10, 5));
        earlyAccess.setEndDate(LocalDate.of(2026, 10, 7));
        Paper fullWeek = sitting("Full week", correlation);
        fullWeek.setStartDate(LocalDate.of(2026, 10, 5));
        fullWeek.setEndDate(LocalDate.of(2026, 10, 9));
        Paper unrelated = sitting("Another paper", other);
        unrelated.setStartDate(LocalDate.of(2026, 10, 20));
        unrelated.setEndDate(LocalDate.of(2026, 10, 22));
        entityManager.flush();
        entityManager.clear();

        var spans = paperRepository.findDateSpans(java.util.List.of(correlation.getId(), other.getId()));

        assertThat(spans).hasSize(2);
        var span = spans.stream().filter(s -> s.getCorrelationId().equals(correlation.getId())).findFirst().orElseThrow();
        assertThat(span.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(span.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        var otherSpan = spans.stream().filter(s -> s.getCorrelationId().equals(other.getId())).findFirst().orElseThrow();
        assertThat(otherSpan.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(otherSpan.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 22));
    }

    @Test
    @DisplayName("a standalone paper's published flag is unaffected by there being correlations around")
    void standaloneListedWithNoCorrelationsAtAll() {
        Paper publishedStandalone = standalone("Published standalone", true);
        standalone("Hidden standalone", false);
        entityManager.clear();

        var page = paperRepository.findWithPublishedLeaderboard(PageRequest.of(0, 50));

        assertThat(page.getContent()).extracting(Paper::getId).containsExactly(publishedStandalone.getId());
    }
}
