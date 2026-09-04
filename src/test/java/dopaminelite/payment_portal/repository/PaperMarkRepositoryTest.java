package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.config.JpaConfig;
import dopaminelite.payment_portal.entity.MarkStudentSnapshot;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperMark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-slice test for paper marks: the (paper, student) uniqueness guarantee, the
 * scheme-lock count, and the paper-scoped single-mark lookup. Uses {@code @DataJpaTest} against
 * the real Liquibase migrations, mirroring {@link PaperSlotRepositoryTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
@DisplayName("Paper Mark Repository Tests")
class PaperMarkRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PaperMarkRepository paperMarkRepository;

    private Paper paper;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        paper = new Paper();
        paper.setTitle("Test Paper");
        paper.setStartDate(LocalDate.now().minusDays(1));
        paper.setEndDate(LocalDate.now().plusDays(1));
        paper.setMcqMaxMarks(new BigDecimal("40.000"));
        paper.setStructuredMaxMarks(new BigDecimal("60.000"));
        entityManager.persist(paper);

        studentId = UUID.randomUUID();
        entityManager.flush();
    }

    private PaperMark newMark(Paper paper, UUID studentId) {
        MarkStudentSnapshot snapshot = new MarkStudentSnapshot();
        snapshot.setCodeNumber("STU-001");
        snapshot.setFullName("Test Student");
        snapshot.setEmail("student@example.com");
        snapshot.setWhatsappNumber("0770000000");

        PaperMark mark = new PaperMark();
        mark.setPaper(paper);
        mark.setStudentId(studentId);
        mark.setStudentSnapshot(snapshot);
        mark.setMcqMarks(new BigDecimal("33.333"));
        mark.setStructuredMarks(new BigDecimal("50.000"));
        mark.setTotalMarks(new BigDecimal("83.333"));
        mark.setEnteredByInstructorId(UUID.randomUUID());
        mark.setLastUpdatedByInstructorId(UUID.randomUUID());
        return mark;
    }

    @Test
    @DisplayName("countByPaperId is 0 before any mark exists, 1 after one is created")
    void countByPaperId_reflectsMarkCount() {
        assertThat(paperMarkRepository.countByPaperId(paper.getId())).isZero();

        entityManager.persist(newMark(paper, studentId));
        entityManager.flush();

        assertThat(paperMarkRepository.countByPaperId(paper.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("existsByPaperIdAndStudentId is true only for the exact (paper, student) pair")
    void existsByPaperIdAndStudentId_scopedToPairs() {
        entityManager.persist(newMark(paper, studentId));
        entityManager.flush();

        assertThat(paperMarkRepository.existsByPaperIdAndStudentId(paper.getId(), studentId)).isTrue();
        assertThat(paperMarkRepository.existsByPaperIdAndStudentId(paper.getId(), UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("the unique (paper_id, student_id) constraint rejects a duplicate mark")
    void uniqueConstraint_rejectsDuplicateStudentOnSamePaper() {
        entityManager.persist(newMark(paper, studentId));
        entityManager.flush();

        PaperMark duplicate = newMark(paper, studentId);
        assertThatThrownBy(() -> paperMarkRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByIdAndPaperId scopes the lookup so a mark from another paper 404s")
    void findByIdAndPaperId_scopedToOwningPaper() {
        PaperMark mark = newMark(paper, studentId);
        entityManager.persist(mark);
        entityManager.flush();

        assertThat(paperMarkRepository.findByIdAndPaperId(mark.getId(), paper.getId())).isPresent();
        assertThat(paperMarkRepository.findByIdAndPaperId(mark.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findByPaperId returns marks for that paper only, eagerly fetching the paper")
    void findByPaperId_returnsMarksForPaper() {
        entityManager.persist(newMark(paper, studentId));

        Paper otherPaper = new Paper();
        otherPaper.setTitle("Other Paper");
        otherPaper.setStartDate(LocalDate.now().minusDays(1));
        otherPaper.setEndDate(LocalDate.now().plusDays(1));
        entityManager.persist(otherPaper);
        entityManager.persist(newMark(otherPaper, UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);
        var page = paperMarkRepository.findByPaperId(paper.getId(), pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getPaper().getId()).isEqualTo(paper.getId());
    }

}
