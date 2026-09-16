package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.config.JpaConfig;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.StudentSnapshot;
import dopaminelite.payment_portal.entity.enums.PaperWritingMode;
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

    private PaymentSubmission submissionWith(PaperWritingMode mode, String centerId, String codeNumber) {
        StudentSnapshot snapshot = new StudentSnapshot();
        snapshot.setFullName("Student " + codeNumber);
        snapshot.setEmail(codeNumber.toLowerCase() + "@example.com");
        snapshot.setWhatsappNumber("0770000000");
        snapshot.setAddress("123 Test Street");
        snapshot.setCodeNumber(codeNumber);
        snapshot.setPaperWritingMode(mode);
        snapshot.setPaperCenterId(centerId);

        PaymentSubmission sub = new PaymentSubmission();
        sub.setStudentId(UUID.randomUUID());
        sub.setPortal(portal);
        sub.setStatus(SubmissionStatus.APPROVED);
        sub.setPortalNameAtSubmission(portal.getDisplayName());
        sub.setStudentSnapshot(snapshot);
        entityManager.persist(sub);
        return sub;
    }

    private void slotFor(PaymentSubmission sub, LocalDateTime consumedAt) {
        PaperSlot slot = new PaperSlot();
        slot.setPaper(paper);
        slot.setPaymentSubmission(sub);
        slot.setConsumedAt(consumedAt);
        entityManager.persist(slot);
    }

    @Test
    @DisplayName("aggregateAttendanceByCenter groups opened/attended counts by center, excluding non-PHYSICAL students")
    void aggregateAttendanceByCenter_groupsCorrectly() {
        String colomboId = UUID.randomUUID().toString();
        String kandyId = UUID.randomUUID().toString();

        PaymentSubmission colomboAttended = submissionWith(PaperWritingMode.PHYSICAL, colomboId, "STU-A");
        PaymentSubmission colomboAbsent = submissionWith(PaperWritingMode.PHYSICAL, colomboId, "STU-B");
        PaymentSubmission kandyAbsent = submissionWith(PaperWritingMode.PHYSICAL, kandyId, "STU-C");
        PaymentSubmission onlineStudent = submissionWith(PaperWritingMode.ONLINE, null, "STU-D");
        PaymentSubmission noCenter = submissionWith(PaperWritingMode.PHYSICAL, null, "STU-E");

        slotFor(colomboAttended, LocalDateTime.now());
        slotFor(colomboAbsent, null);
        slotFor(kandyAbsent, null);
        slotFor(onlineStudent, null);
        slotFor(noCenter, null);
        entityManager.flush();
        entityManager.clear();

        List<PaperSlotRepository.CenterAttendanceAggregate> rows =
                paperSlotRepository.aggregateAttendanceByCenter(paper.getId(), PaperWritingMode.PHYSICAL);

        // Only PHYSICAL students counted (4), grouped into 3 buckets: Colombo, Kandy, null.
        assertThat(rows).hasSize(3);

        var colomboRow = rows.stream().filter(r -> colomboId.equals(r.getCenterId())).findFirst().orElseThrow();
        assertThat(colomboRow.getOpened()).isEqualTo(2L);
        assertThat(colomboRow.getAttended()).isEqualTo(1L);

        var kandyRow = rows.stream().filter(r -> kandyId.equals(r.getCenterId())).findFirst().orElseThrow();
        assertThat(kandyRow.getOpened()).isEqualTo(1L);
        assertThat(kandyRow.getAttended()).isEqualTo(0L);

        var nullRow = rows.stream().filter(r -> r.getCenterId() == null).findFirst().orElseThrow();
        assertThat(nullRow.getOpened()).isEqualTo(1L);

        long totalOpened = rows.stream().mapToLong(PaperSlotRepository.CenterAttendanceAggregate::getOpened).sum();
        assertThat(totalOpened).isEqualTo(4L); // the ONLINE student's slot is excluded entirely
    }

    @Test
    @DisplayName("findAttendanceStudents: null centerId matches everyone, 'UNASSIGNED' matches only no-center students, else exact match")
    void findAttendanceStudents_centerIdModes() {
        String colomboId = UUID.randomUUID().toString();
        PaymentSubmission withCenter = submissionWith(PaperWritingMode.PHYSICAL, colomboId, "STU-A");
        PaymentSubmission withoutCenter = submissionWith(PaperWritingMode.PHYSICAL, null, "STU-B");

        slotFor(withCenter, null);
        slotFor(withoutCenter, null);
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);

        var everyone = paperSlotRepository.findAttendanceStudents(paper.getId(), null, null, pageable);
        assertThat(everyone.getTotalElements()).isEqualTo(2);

        var unassignedBucket = paperSlotRepository.findAttendanceStudents(paper.getId(), "UNASSIGNED", null, pageable);
        assertThat(unassignedBucket.getTotalElements()).isEqualTo(1);
        assertThat(unassignedBucket.getContent().get(0).getPaymentSubmission().getStudentSnapshot().getCodeNumber())
                .isEqualTo("STU-B");

        var colomboBucket = paperSlotRepository.findAttendanceStudents(paper.getId(), colomboId, null, pageable);
        assertThat(colomboBucket.getTotalElements()).isEqualTo(1);
        assertThat(colomboBucket.getContent().get(0).getPaymentSubmission().getStudentSnapshot().getCodeNumber())
                .isEqualTo("STU-A");
    }

    @Test
    @DisplayName("findAttendanceStudents: ONLINE students are excluded entirely, even with no center filter")
    void findAttendanceStudents_excludesOnlineStudents() {
        PaymentSubmission physicalSub = submissionWith(PaperWritingMode.PHYSICAL, null, "STU-A");
        PaymentSubmission onlineSub = submissionWith(PaperWritingMode.ONLINE, null, "STU-B");

        slotFor(physicalSub, null);
        slotFor(onlineSub, null);
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);
        var results = paperSlotRepository.findAttendanceStudents(paper.getId(), null, null, pageable);

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getPaymentSubmission().getStudentSnapshot().getCodeNumber())
                .isEqualTo("STU-A");
    }

    @Test
    @DisplayName("findAttendanceStudents: attended filter matches only consumed/unconsumed slots as requested")
    void findAttendanceStudents_attendedFilter() {
        PaymentSubmission attendedSub = submissionWith(PaperWritingMode.PHYSICAL, null, "STU-A");
        PaymentSubmission absentSub = submissionWith(PaperWritingMode.PHYSICAL, null, "STU-B");

        slotFor(attendedSub, LocalDateTime.now());
        slotFor(absentSub, null);
        entityManager.flush();
        entityManager.clear();

        Pageable pageable = PageRequest.of(0, 10);

        var attendedOnly = paperSlotRepository.findAttendanceStudents(paper.getId(), null, true, pageable);
        assertThat(attendedOnly.getTotalElements()).isEqualTo(1);
        assertThat(attendedOnly.getContent().get(0).getPaymentSubmission().getStudentSnapshot().getCodeNumber())
                .isEqualTo("STU-A");

        var absentOnly = paperSlotRepository.findAttendanceStudents(paper.getId(), null, false, pageable);
        assertThat(absentOnly.getTotalElements()).isEqualTo(1);
        assertThat(absentOnly.getContent().get(0).getPaymentSubmission().getStudentSnapshot().getCodeNumber())
                .isEqualTo("STU-B");

        var both = paperSlotRepository.findAttendanceStudents(paper.getId(), null, null, pageable);
        assertThat(both.getTotalElements()).isEqualTo(2);
    }

}
