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
    @DisplayName("existsByOwnerIdAndStudentId is true only for the exact (owner, student) pair")
    void existsByOwnerIdAndStudentId_scopedToPairs() {
        entityManager.persist(newMark(paper, studentId));
        entityManager.flush();

        assertThat(paperMarkRepository.existsByOwnerIdAndStudentId(paper.getId(), studentId)).isTrue();
        assertThat(paperMarkRepository.existsByOwnerIdAndStudentId(paper.getId(), UUID.randomUUID())).isFalse();
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
    @DisplayName("findByIdAndOwnerId scopes the lookup so a mark from another paper 404s")
    void findByIdAndOwnerId_scopedToOwningPaper() {
        PaperMark mark = newMark(paper, studentId);
        entityManager.persist(mark);
        entityManager.flush();

        assertThat(paperMarkRepository.findByIdAndOwnerId(mark.getId(), paper.getId())).isPresent();
        assertThat(paperMarkRepository.findByIdAndOwnerId(mark.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findByFilters with no filters returns marks for that paper only, eagerly fetching the paper")
    void findByFilters_returnsMarksForPaper() {
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
        var page = paperMarkRepository.findByFilters(paper.getId(), null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getPaper().getId()).isEqualTo(paper.getId());
    }

    @Test
    @DisplayName("findByStudentId returns a student's marks across every paper, eagerly fetching each paper")
    void findByStudentId_returnsMarksAcrossPapers() {
        Paper otherPaper = new Paper();
        otherPaper.setTitle("Other Paper");
        otherPaper.setStartDate(LocalDate.now().minusDays(10));
        otherPaper.setEndDate(LocalDate.now().minusDays(9));
        entityManager.persist(otherPaper);

        entityManager.persist(newMark(paper, studentId));
        entityManager.persist(newMark(otherPaper, studentId));
        entityManager.persist(newMark(paper, UUID.randomUUID())); // a different student on `paper`
        entityManager.flush();
        entityManager.clear();

        var marks = paperMarkRepository.findByStudentId(studentId);

        assertThat(marks).hasSize(2);
        assertThat(marks).extracting(m -> m.getPaper().getId())
                .containsExactlyInAnyOrder(paper.getId(), otherPaper.getId());
    }

    @Test
    @DisplayName("findByStudentId returns an empty list for a student with no marks")
    void findByStudentId_emptyForStudentWithNoMarks() {
        assertThat(paperMarkRepository.findByStudentId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findByFilters narrows to that instructor's own entries on the paper")
    void findByFilters_scopedToInstructor() {
        UUID instructorA = UUID.randomUUID();
        UUID instructorB = UUID.randomUUID();

        PaperMark markByA = newMark(paper, studentId);
        markByA.setEnteredByInstructorId(instructorA);
        entityManager.persist(markByA);

        PaperMark markByB = newMark(paper, UUID.randomUUID());
        markByB.setEnteredByInstructorId(instructorB);
        entityManager.persist(markByB);
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);
        var page = paperMarkRepository.findByFilters(paper.getId(), instructorA, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getEnteredByInstructorId()).isEqualTo(instructorA);

        // An instructor with no entries on this paper gets an empty page, not an error.
        var emptyPage = paperMarkRepository.findByFilters(paper.getId(), UUID.randomUUID(), null, pageable);
        assertThat(emptyPage.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("findByFilters matches a student's whole code number, case-insensitively, and only that student")
    void findByFilters_matchesWholeCodeNumber() {
        PaperMark target = newMark(paper, studentId);
        target.getStudentSnapshot().setCodeNumber("STU-042");
        entityManager.persist(target);

        PaperMark other = newMark(paper, UUID.randomUUID());
        other.getStudentSnapshot().setCodeNumber("STU-043");
        entityManager.persist(other);
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);

        var exact = paperMarkRepository.findByFilters(paper.getId(), null, "STU-042", pageable);
        assertThat(exact.getTotalElements()).isEqualTo(1);
        assertThat(exact.getContent().get(0).getStudentSnapshot().getCodeNumber()).isEqualTo("STU-042");

        // Case is not the instructor's problem
        assertThat(paperMarkRepository.findByFilters(paper.getId(), null, "stu-042", pageable).getTotalElements())
                .isEqualTo(1);

        // A partial code matches nothing — this is a whole-code lookup, not a substring search
        assertThat(paperMarkRepository.findByFilters(paper.getId(), null, "STU-04", pageable).getTotalElements())
                .isZero();

        // A code with no mark on this paper is an empty page, not an error
        assertThat(paperMarkRepository.findByFilters(paper.getId(), null, "STU-999", pageable).getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("findByFilters combines the instructor and code-number filters")
    void findByFilters_combinesInstructorAndCodeNumber() {
        UUID instructorA = UUID.randomUUID();
        UUID instructorB = UUID.randomUUID();

        PaperMark byA = newMark(paper, studentId);
        byA.getStudentSnapshot().setCodeNumber("STU-100");
        byA.setEnteredByInstructorId(instructorA);
        entityManager.persist(byA);

        PaperMark byB = newMark(paper, UUID.randomUUID());
        byB.getStudentSnapshot().setCodeNumber("STU-200");
        byB.setEnteredByInstructorId(instructorB);
        entityManager.persist(byB);
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);

        // A's own mark, found by its code
        assertThat(paperMarkRepository.findByFilters(paper.getId(), instructorA, "STU-100", pageable)
                .getTotalElements()).isEqualTo(1);

        // B's mark is excluded once the list is narrowed to A's own entries, though searching
        // without that narrowing does find it — which is what lets an instructor look anyone up.
        assertThat(paperMarkRepository.findByFilters(paper.getId(), instructorA, "STU-200", pageable)
                .getTotalElements()).isZero();
        assertThat(paperMarkRepository.findByFilters(paper.getId(), null, "STU-200", pageable)
                .getTotalElements()).isEqualTo(1);
    }

}
