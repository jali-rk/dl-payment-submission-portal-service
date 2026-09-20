package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.paper.PaperUpdateRequest;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperCorrelation;
import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.StudentSnapshot;
import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.repository.PaperCorrelationRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
import dopaminelite.payment_portal.repository.PaperSlotCreationResultRepository;
import dopaminelite.payment_portal.repository.PaperSlotRepository;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the reason correlations exist: a paper sat in the first week of a month is created as two
 * paper-events — an early-access window on last month's portal, the full window on this month's —
 * and a student who paid both months used to receive a slot from each, i.e. two QR codes for one
 * paper, each independently scannable.
 *
 * <p>Deliberately <em>not</em> {@code @Transactional}, unlike the other service tests here:
 * {@code PaperSlotWriter} does its work in {@code REQUIRES_NEW} transactions that commit
 * independently of any surrounding one, so a test-managed rollback wouldn't undo them (and, worse,
 * would hide the very cross-transaction visibility this feature depends on — a slot created earlier
 * in the approval loop has to be findable by a later iteration). Rows are cleaned up explicitly
 * instead.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Paper Correlation Slot Tests")
class PaperCorrelationSlotServiceTest {

    @Autowired
    private PaperSlotService paperSlotService;

    @Autowired
    private PaperService paperService;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperCorrelationRepository correlationRepository;

    @Autowired
    private PaperSlotRepository paperSlotRepository;

    @Autowired
    private PaperSlotCreationResultRepository resultRepository;

    @Autowired
    private PaymentPortalRepository portalRepository;

    @Autowired
    private PaymentSubmissionRepository submissionRepository;

    private static final LocalDate MONDAY = LocalDate.now().minusDays(1);
    private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);

    // Next month's paper, still ahead of its window - the shape of a paper-event that can still be
    // moved into a correlation, and the only one late tagging ever applies to.
    private static final LocalDate NEXT_MONDAY = LocalDate.now().plusDays(7);
    private static final LocalDate NEXT_WEDNESDAY = NEXT_MONDAY.plusDays(2);
    private static final LocalDate NEXT_FRIDAY = NEXT_MONDAY.plusDays(4);

    private UUID studentId;

    @BeforeEach
    void setUp() {
        cleanUp();
        studentId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        resultRepository.deleteAll();
        paperSlotRepository.deleteAll();
        paperRepository.deleteAll();
        correlationRepository.deleteAll();
        submissionRepository.deleteAll();
        portalRepository.deleteAll();
    }

    @Test
    @DisplayName("a second payment extends the existing slot instead of issuing a second QR code")
    void secondPayment_extendsRatherThanDuplicates() {
        PaperCorrelation correlation = correlation("OCT-W1");
        PaymentPortal september = portal("September");
        PaymentPortal october = portal("October");
        paper("Early access", MONDAY, WEDNESDAY, correlation, september);
        paper("Full week", MONDAY, FRIDAY, correlation, october);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(september).getId());
        assertThat(paperSlotRepository.findAll()).hasSize(1);
        assertThat(effectiveEnd(paperSlotRepository.findAll().get(0))).isEqualTo(WEDNESDAY);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(october).getId());

        List<PaperSlot> slots = paperSlotRepository.findAll();
        assertThat(slots).hasSize(1);
        assertThat(effectiveEnd(slots.get(0))).isEqualTo(FRIDAY);
    }

    @Test
    @DisplayName("approving in the reverse order never shortens the window")
    void reverseOrder_neverShrinksTheWindow() {
        PaperCorrelation correlation = correlation("OCT-W1");
        PaymentPortal september = portal("September");
        PaymentPortal october = portal("October");
        paper("Early access", MONDAY, WEDNESDAY, correlation, september);
        paper("Full week", MONDAY, FRIDAY, correlation, october);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(october).getId());
        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(september).getId());

        List<PaperSlot> slots = paperSlotRepository.findAll();
        assertThat(slots).hasSize(1);
        assertThat(effectiveEnd(slots.get(0))).isEqualTo(FRIDAY);
    }

    @Test
    @DisplayName("extending an already-consumed slot leaves it consumed")
    void extendingConsumedSlot_staysConsumed() {
        PaperCorrelation correlation = correlation("OCT-W1");
        PaymentPortal september = portal("September");
        PaymentPortal october = portal("October");
        paper("Early access", MONDAY, WEDNESDAY, correlation, september);
        paper("Full week", MONDAY, FRIDAY, correlation, october);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(september).getId());
        PaperSlot slot = paperSlotRepository.findAll().get(0);
        LocalDateTime consumedAt = LocalDateTime.now();
        slot.setConsumedAt(consumedAt);
        slot.setConsumedByInstructorId(UUID.randomUUID());
        paperSlotRepository.saveAndFlush(slot);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(october).getId());

        List<PaperSlot> slots = paperSlotRepository.findAll();
        assertThat(slots).hasSize(1);
        assertThat(effectiveEnd(slots.get(0))).isEqualTo(FRIDAY);
        assertThat(slots.get(0).getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("one portal carrying a correlated and an uncorrelated paper-event yields one slot each")
    void onePortal_mixedCorrelatedAndUncorrelated() {
        PaymentPortal september = portal("September");
        paper("Standalone paper", MONDAY, WEDNESDAY, null, september);
        paper("Correlated paper", MONDAY, FRIDAY, correlation("OCT-W1"), september);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(september).getId());

        assertThat(paperSlotRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("one portal carrying both sittings of the same paper yields a single slot")
    void onePortal_bothSittingsOfOneCorrelation() {
        PaperCorrelation correlation = correlation("OCT-W1");
        PaymentPortal september = portal("September");
        paper("Early access", MONDAY, WEDNESDAY, correlation, september);
        paper("Full week", MONDAY, FRIDAY, correlation, september);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(september).getId());

        List<PaperSlot> slots = paperSlotRepository.findAll();
        assertThat(slots).hasSize(1);
        // Whichever of the two the loop reached first created the slot; the other extended it, so
        // the window is the later end date either way.
        assertThat(effectiveEnd(slots.get(0))).isEqualTo(FRIDAY);
    }

    @Test
    @DisplayName("two different correlations on one portal don't interfere")
    void onePortal_twoDistinctCorrelations() {
        PaymentPortal september = portal("September");
        paper("Week one", MONDAY, WEDNESDAY, correlation("OCT-W1"), september);
        paper("Week two", MONDAY, FRIDAY, correlation("OCT-W2"), september);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(september).getId());

        assertThat(paperSlotRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("re-approving the same submission creates nothing new and extends nothing twice")
    void reApproval_isIdempotent() {
        PaperCorrelation correlation = correlation("OCT-W1");
        PaymentPortal september = portal("September");
        paper("Early access", MONDAY, WEDNESDAY, correlation, september);
        PaymentSubmission submission = approvedSubmission(september);

        paperSlotService.createSlotsForApprovedSubmission(submission.getId());
        paperSlotService.createSlotsForApprovedSubmission(submission.getId());
        paperSlotService.createSlotsForApprovedSubmission(submission.getId());

        List<PaperSlot> slots = paperSlotRepository.findAll();
        assertThat(slots).hasSize(1);
        assertThat(effectiveEnd(slots.get(0))).isEqualTo(WEDNESDAY);
    }

    @Test
    @DisplayName("a paper-event with no correlation still gets its own slot per payment, as before")
    void uncorrelatedPaper_behavesAsBefore() {
        PaymentPortal september = portal("September");
        PaymentPortal october = portal("October");
        paper("Standalone A", MONDAY, WEDNESDAY, null, september);
        paper("Standalone B", MONDAY, FRIDAY, null, october);

        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(september).getId());
        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(october).getId());

        // Two unrelated papers, so two slots - no extension, no deduplication.
        List<PaperSlot> slots = paperSlotRepository.findAll();
        assertThat(slots).hasSize(2);
        assertThat(slots).allSatisfy(slot -> assertThat(slot.getValidUntil()).isNull());
    }

    @Test
    @DisplayName("the one QR code is reported as covering both payments that entitle the student to it")
    void extendedSlot_reportsBothPaymentsItCovers() {
        PaperCorrelation correlation = correlation("OCT-W1");
        PaymentPortal september = portal("September");
        PaymentPortal october = portal("October");
        paper("Early access", MONDAY, WEDNESDAY, correlation, september);
        paper("Full week", MONDAY, FRIDAY, correlation, october);

        UUID septemberPayment = approvedSubmission(september).getId();
        UUID octoberPayment = approvedSubmission(october).getId();
        paperSlotService.createSlotsForApprovedSubmission(septemberPayment);
        paperSlotService.createSlotsForApprovedSubmission(octoberPayment);

        var slots = paperSlotService.listSlots(null, studentId, null, null, 20, 0);

        assertThat(slots.getItems()).hasSize(1);
        // Both months' cards on the student's payments page need to show this one code.
        assertThat(slots.getItems().get(0).getCoveredSubmissionIds())
                .containsExactlyInAnyOrder(septemberPayment, octoberPayment);
    }

    @Test
    @DisplayName("tagging a paper-event late brings its existing slots into the correlation")
    void taggingLate_backfillsExistingSlots() {
        PaymentPortal october = portal("October");
        PaymentPortal november = portal("November");
        // The early-access sitting goes live and starts issuing slots before anyone thinks to
        // group it - the case the backfill exists for.
        Paper earlyAccess = paper("Early access", NEXT_MONDAY, NEXT_WEDNESDAY, null, october);
        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(october).getId());
        assertThat(paperSlotRepository.findAll()).hasSize(1);

        PaperCorrelation correlation = correlation("NOV-W1");
        paperService.updatePaper(earlyAccess.getId(), updateWithCorrelation(correlation));
        paper("Full week", NEXT_MONDAY, NEXT_FRIDAY, correlation, november);

        // Without the backfill that first slot would be invisible to the duplicate check and this
        // student would walk away with a second QR code.
        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(november).getId());

        List<PaperSlot> slots = paperSlotRepository.findAll();
        assertThat(slots).hasSize(1);
        assertThat(effectiveEnd(slots.get(0))).isEqualTo(NEXT_FRIDAY);
    }

    @Test
    @DisplayName("tagging is refused, naming the student, when it would leave someone holding two slots")
    void taggingLate_refusedWhenItWouldDuplicate() {
        PaperCorrelation correlation = correlation("NOV-W1");
        PaymentPortal october = portal("October");
        PaymentPortal november = portal("November");
        // This sitting was grouped from the start; the other never was, so the student who paid
        // both months already holds two codes.
        Paper untagged = paper("Early access", NEXT_MONDAY, NEXT_WEDNESDAY, null, october);
        paper("Full week", NEXT_MONDAY, NEXT_FRIDAY, correlation, november);
        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(october).getId());
        paperSlotService.createSlotsForApprovedSubmission(approvedSubmission(november).getId());
        assertThat(paperSlotRepository.findAll()).hasSize(2);

        assertThatThrownBy(() ->
                paperService.updatePaper(untagged.getId(), updateWithCorrelation(correlation)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Test Student")
                .hasMessageContaining("STU-001");

        // Refused rather than half-applied, and nothing was deleted behind the admin's back.
        assertThat(paperSlotRepository.findAll()).hasSize(2);
    }

    /**
     * The slot's usable end date, resolved without an open session: the entities these tests read
     * back are detached, so {@code PaperSlot.effectiveEndDate()} can't lazily load its paper here
     * the way it can inside a request.
     */
    private LocalDate effectiveEnd(PaperSlot slot) {
        return slot.getValidUntil() != null
                ? slot.getValidUntil()
                : paperRepository.findById(slot.getPaper().getId()).orElseThrow().getEndDate();
    }

    // --- fixtures ---------------------------------------------------------------------------

    private PaperUpdateRequest updateWithCorrelation(PaperCorrelation correlation) {
        PaperUpdateRequest request = new PaperUpdateRequest();
        request.setCorrelationId(correlation.getId());
        return request;
    }

    private PaperCorrelation correlation(String code) {
        PaperCorrelation correlation = new PaperCorrelation();
        correlation.setCode(code);
        correlation.setDisplayName("Paper " + code);
        return correlationRepository.saveAndFlush(correlation);
    }

    private PaymentPortal portal(String name) {
        PaymentPortal portal = new PaymentPortal();
        portal.setMonth(1);
        portal.setYear(2026);
        portal.setName(name.toLowerCase() + "-" + UUID.randomUUID());
        portal.setDisplayName(name);
        portal.setIsPublished(true);
        portal.setVisibility(PortalVisibility.PUBLISHED);
        return portalRepository.saveAndFlush(portal);
    }

    private Paper paper(String title, LocalDate start, LocalDate end,
                        PaperCorrelation correlation, PaymentPortal... portals) {
        Paper paper = new Paper();
        paper.setTitle(title);
        paper.setStartDate(start);
        paper.setEndDate(end);
        paper.setCorrelation(correlation);
        paper.setLinkedPortals(List.of(portals));
        return paperRepository.saveAndFlush(paper);
    }

    /** An approved payment by the same student, so every one of them matches the same correlation. */
    private PaymentSubmission approvedSubmission(PaymentPortal portal) {
        StudentSnapshot snapshot = new StudentSnapshot();
        snapshot.setFullName("Test Student");
        snapshot.setEmail("student@example.com");
        snapshot.setWhatsappNumber("0770000000");
        snapshot.setAddress("123 Test Street");
        snapshot.setCodeNumber("STU-001");

        PaymentSubmission submission = new PaymentSubmission();
        submission.setStudentId(studentId);
        submission.setPortal(portal);
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setPortalNameAtSubmission(portal.getDisplayName());
        submission.setStudentSnapshot(snapshot);
        return submissionRepository.saveAndFlush(submission);
    }

}
