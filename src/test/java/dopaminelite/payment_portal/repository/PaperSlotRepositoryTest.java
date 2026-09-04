package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.config.JpaConfig;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.StudentSnapshot;
import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-slice test for the paper slot lifecycle: linking papers to portals, the atomic
 * exactly-once consume query, and the SQL-side status filter. Uses {@code @DataJpaTest} (not
 * {@code @SpringBootTest}) so it runs the real Liquibase migrations against the configured
 * test datasource without pulling in unrelated service beans.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
@DisplayName("Paper Slot Repository Tests")
class PaperSlotRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperSlotRepository paperSlotRepository;

    private PaymentPortal portal;
    private Paper paper;
    private PaymentSubmission submission;

    @BeforeEach
    void setUp() {
        portal = new PaymentPortal();
        portal.setMonth(1);
        portal.setYear(2026);
        portal.setName("test-portal-" + UUID.randomUUID());
        portal.setDisplayName("Test Portal");
        portal.setIsPublished(true);
        portal.setVisibility(PortalVisibility.PUBLISHED);
        entityManager.persist(portal);

        paper = new Paper();
        paper.setTitle("Test Paper");
        paper.setDescription("A paper for testing");
        paper.setStartDate(LocalDate.now().minusDays(1));
        paper.setEndDate(LocalDate.now().plusDays(1));
        paper.setLinkedPortals(List.of(portal));
        entityManager.persist(paper);

        StudentSnapshot snapshot = new StudentSnapshot();
        snapshot.setFullName("Test Student");
        snapshot.setEmail("student@example.com");
        snapshot.setWhatsappNumber("0770000000");
        snapshot.setAddress("123 Test Street");
        snapshot.setCodeNumber("STU-001");

        submission = new PaymentSubmission();
        submission.setStudentId(UUID.randomUUID());
        submission.setPortal(portal);
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setPortalNameAtSubmission(portal.getDisplayName());
        submission.setStudentSnapshot(snapshot);
        entityManager.persist(submission);

        entityManager.flush();
    }

    @Test
    @DisplayName("findByLinkedPortalId finds papers linked to a portal")
    void findByLinkedPortalId_findsLinkedPaper() {
        List<Paper> found = paperRepository.findByLinkedPortalId(portal.getId());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(paper.getId());
    }

    @Test
    @DisplayName("consumeIfAvailable succeeds exactly once for an available slot")
    void consumeIfAvailable_succeedsOnce_thenRejectsSecondAttempt() {
        PaperSlot slot = new PaperSlot();
        slot.setPaper(paper);
        slot.setPaymentSubmission(submission);
        entityManager.persist(slot);
        entityManager.flush();

        UUID instructorId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        int firstAttempt = paperSlotRepository.consumeIfAvailable(slot.getId(), now, instructorId, today);
        assertThat(firstAttempt).isEqualTo(1);

        int secondAttempt = paperSlotRepository.consumeIfAvailable(slot.getId(), now, instructorId, today);
        assertThat(secondAttempt).isEqualTo(0);

        entityManager.clear();
        PaperSlot reloaded = entityManager.find(PaperSlot.class, slot.getId());
        assertThat(reloaded.getConsumedAt()).isNotNull();
        assertThat(reloaded.getConsumedByInstructorId()).isEqualTo(instructorId);
    }

    @Test
    @DisplayName("consumeIfAvailable rejects a slot outside the paper's validity window")
    void consumeIfAvailable_rejectsOutsideWindow() {
        paper.setStartDate(LocalDate.now().minusDays(10));
        paper.setEndDate(LocalDate.now().minusDays(5));
        entityManager.persist(paper);

        PaperSlot slot = new PaperSlot();
        slot.setPaper(paper);
        slot.setPaymentSubmission(submission);
        entityManager.persist(slot);
        entityManager.flush();

        int result = paperSlotRepository.consumeIfAvailable(
                slot.getId(), LocalDateTime.now(), UUID.randomUUID(), LocalDate.now());

        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("findByFilters filters by computed AVAILABLE status entirely in SQL")
    void findByFilters_filtersByComputedStatus() {
        PaperSlot slot = new PaperSlot();
        slot.setPaper(paper);
        slot.setPaymentSubmission(submission);
        entityManager.persist(slot);
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);
        LocalDate today = LocalDate.now();

        var available = paperSlotRepository.findByFilters(null, null, null, "AVAILABLE", today, pageable);
        assertThat(available.getTotalElements()).isEqualTo(1);

        var consumed = paperSlotRepository.findByFilters(null, null, null, "CONSUMED", today, pageable);
        assertThat(consumed.getTotalElements()).isEqualTo(0);

        var byStudentCode = paperSlotRepository.findByFilters(null, null, "STU-001", null, today, pageable);
        assertThat(byStudentCode.getTotalElements()).isEqualTo(1);
    }

}
