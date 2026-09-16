package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.StudentSnapshot;
import dopaminelite.payment_portal.entity.enums.PaperWritingMode;
import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.repository.PaperRepository;
import dopaminelite.payment_portal.repository.PaperSlotRepository;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import dopaminelite.payment_portal.service.PaperCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller test for the paper-center-attendance dashboard's read endpoints. Mirrors
 * {@link PaperLeaderboardControllerTest}'s style (real H2-backed {@code @SpringBootTest}, rolled
 * back per test via {@code @Transactional}), stubbing {@link PaperCenterService} the same way
 * that test stubs {@code StudentLookupService} - it's the one collaborator that would otherwise
 * make a real HTTP call to the BFF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Paper Center Attendance API Tests")
class PaperAttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaymentPortalRepository paymentPortalRepository;

    @Autowired
    private PaymentSubmissionRepository paymentSubmissionRepository;

    @Autowired
    private PaperSlotRepository paperSlotRepository;

    @MockitoBean
    private PaperCenterService paperCenterService;

    private static final String COLOMBO_ID = UUID.randomUUID().toString();
    private static final String KANDY_ID = UUID.randomUUID().toString();
    private static final String LEGACY_NAME_AS_ID = "Galle Legacy Center"; // simulates the known name-in-ID-column bug

    private PaymentPortal portal;
    private Paper paper;

    @BeforeEach
    void setUp() {
        when(paperCenterService.getPaperCenterNameMap()).thenReturn(Map.of(
                COLOMBO_ID, "Colombo Main Center",
                KANDY_ID, "Kandy Central Hall"
        ));

        PaymentPortal newPortal = new PaymentPortal();
        newPortal.setMonth(10);
        newPortal.setYear(2026);
        newPortal.setName("attendance-test-portal-" + UUID.randomUUID());
        newPortal.setDisplayName("Attendance Test Portal");
        newPortal.setIsPublished(true);
        newPortal.setVisibility(PortalVisibility.PUBLISHED);
        portal = paymentPortalRepository.save(newPortal);
    }

    private Paper createPaper() {
        Paper p = new Paper();
        p.setTitle("October Physics");
        p.setStartDate(LocalDate.now().minusDays(1));
        p.setEndDate(LocalDate.now().plusDays(1));
        p.setLinkedPortals(List.of(portal));
        return paperRepository.save(p);
    }

    private PaymentSubmission createSubmission(PaperWritingMode mode, String centerId, String codeNumber, String fullName) {
        StudentSnapshot snapshot = new StudentSnapshot();
        snapshot.setFullName(fullName);
        snapshot.setEmail(codeNumber.toLowerCase() + "@example.com");
        snapshot.setWhatsappNumber("0770000000");
        snapshot.setAddress("123 Test Street");
        snapshot.setCodeNumber(codeNumber);
        snapshot.setPaperWritingMode(mode);
        snapshot.setPaperCenterId(centerId);

        PaymentSubmission submission = new PaymentSubmission();
        submission.setStudentId(UUID.randomUUID());
        submission.setPortal(portal);
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setPortalNameAtSubmission(portal.getDisplayName());
        submission.setStudentSnapshot(snapshot);
        return paymentSubmissionRepository.save(submission);
    }

    private PaperSlot createSlot(Paper paper, PaymentSubmission submission, LocalDateTime consumedAt) {
        PaperSlot slot = new PaperSlot();
        slot.setPaper(paper);
        slot.setPaymentSubmission(submission);
        slot.setConsumedAt(consumedAt);
        if (consumedAt != null) {
            slot.setConsumedByInstructorId(UUID.randomUUID());
        }
        return paperSlotRepository.save(slot);
    }

    @Test
    @DisplayName("GET .../attendance/by-center - active centers with zero slots still appear as zero rows")
    void getByCenterSummary_includesZeroSlotActiveCenters() throws Exception {
        paper = createPaper();

        mockMvc.perform(get("/papers/{paperId}/attendance/by-center", paper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paperTitle").value("October Physics"))
                .andExpect(jsonPath("$.totals.opened").value(0))
                .andExpect(jsonPath("$.totals.attended").value(0))
                .andExpect(jsonPath("$.centers", hasSize(2)))
                .andExpect(jsonPath("$.centers[?(@.paperCenterId == '" + COLOMBO_ID + "')].opened").value(0))
                .andExpect(jsonPath("$.centers[?(@.paperCenterId == '" + KANDY_ID + "')].opened").value(0));
    }

    @Test
    @DisplayName("GET .../attendance/by-center - counts opened/attended/absent/rate correctly per center, excludes ONLINE students")
    void getByCenterSummary_countsCorrectly() throws Exception {
        paper = createPaper();

        PaymentSubmission colomboAttended = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-A", "Student A");
        PaymentSubmission colomboAbsent = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-B", "Student B");
        PaymentSubmission kandyAbsent = createSubmission(PaperWritingMode.PHYSICAL, KANDY_ID, "STU-C", "Student C");
        PaymentSubmission onlineStudent = createSubmission(PaperWritingMode.ONLINE, null, "STU-D", "Student D");

        createSlot(paper, colomboAttended, LocalDateTime.now());
        createSlot(paper, colomboAbsent, null);
        createSlot(paper, kandyAbsent, null);
        createSlot(paper, onlineStudent, null); // must be excluded entirely - not PHYSICAL

        mockMvc.perform(get("/papers/{paperId}/attendance/by-center", paper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.opened").value(3)) // online student excluded
                .andExpect(jsonPath("$.totals.attended").value(1))
                .andExpect(jsonPath("$.totals.absent").value(2))
                .andExpect(jsonPath("$.centers[?(@.paperCenterId == '" + COLOMBO_ID + "')].opened").value(2))
                .andExpect(jsonPath("$.centers[?(@.paperCenterId == '" + COLOMBO_ID + "')].attended").value(1))
                .andExpect(jsonPath("$.centers[?(@.paperCenterId == '" + COLOMBO_ID + "')].attendanceRate").value(50.0))
                .andExpect(jsonPath("$.centers[?(@.paperCenterId == '" + KANDY_ID + "')].opened").value(1))
                .andExpect(jsonPath("$.centers[?(@.paperCenterId == '" + KANDY_ID + "')].attended").value(0));
    }

    @Test
    @DisplayName("GET .../attendance/by-center - legacy name-in-ID-column rows surface as their own bucket instead of vanishing")
    void getByCenterSummary_legacyNameAsIdBucket() throws Exception {
        paper = createPaper();
        PaymentSubmission legacy = createSubmission(PaperWritingMode.PHYSICAL, LEGACY_NAME_AS_ID, "STU-E", "Student E");
        createSlot(paper, legacy, null);

        mockMvc.perform(get("/papers/{paperId}/attendance/by-center", paper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centers[?(@.paperCenterName == '" + LEGACY_NAME_AS_ID + "')].opened").value(1));
    }

    @Test
    @DisplayName("GET .../attendance/by-center - PHYSICAL students missing a center are grouped under 'Not specified', exposed as the UNASSIGNED sentinel id")
    void getByCenterSummary_missingCenterGroupedAsNotSpecified() throws Exception {
        paper = createPaper();
        PaymentSubmission noCenter = createSubmission(PaperWritingMode.PHYSICAL, null, "STU-F", "Student F");
        createSlot(paper, noCenter, null);

        mockMvc.perform(get("/papers/{paperId}/attendance/by-center", paper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.centers[?(@.paperCenterName == 'Not specified')].opened").value(1))
                .andExpect(jsonPath("$.centers[?(@.paperCenterName == 'Not specified')].paperCenterId").value("UNASSIGNED"));
    }

    @Test
    @DisplayName("GET .../attendance/by-center - 404s for a non-existent paper")
    void getByCenterSummary_paperNotFound() throws Exception {
        mockMvc.perform(get("/papers/{paperId}/attendance/by-center", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET .../attendance/students - drills into a specific center's students with attended flag and scan time")
    void getStudents_forSpecificCenter() throws Exception {
        paper = createPaper();
        PaymentSubmission attended = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-A", "Student A");
        PaymentSubmission absent = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-B", "Student B");
        PaymentSubmission other = createSubmission(PaperWritingMode.PHYSICAL, KANDY_ID, "STU-C", "Student C");

        LocalDateTime scanTime = LocalDateTime.now();
        createSlot(paper, attended, scanTime);
        createSlot(paper, absent, null);
        createSlot(paper, other, null);

        mockMvc.perform(get("/papers/{paperId}/attendance/students", paper.getId())
                        .param("centerId", COLOMBO_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.codeNumber == 'STU-A')].attended").value(true))
                .andExpect(jsonPath("$.items[?(@.codeNumber == 'STU-B')].attended").value(false))
                .andExpect(jsonPath("$.items[?(@.codeNumber == 'STU-A')].paperCenterId").value(COLOMBO_ID))
                .andExpect(jsonPath("$.items[?(@.codeNumber == 'STU-A')].paperCenterName").value("Colombo Main Center"));
    }

    @Test
    @DisplayName("GET .../attendance/students - omitting centerId returns every PHYSICAL student, regardless of center")
    void getStudents_omittedCenterId_returnsEveryone() throws Exception {
        paper = createPaper();
        PaymentSubmission withCenter = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-A", "Student A");
        PaymentSubmission withoutCenter = createSubmission(PaperWritingMode.PHYSICAL, null, "STU-B", "Student B");

        createSlot(paper, withCenter, null);
        createSlot(paper, withoutCenter, null);

        mockMvc.perform(get("/papers/{paperId}/attendance/students", paper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    @DisplayName("GET .../attendance/students - ONLINE students are excluded entirely, even with no center filter")
    void getStudents_excludesOnlineStudents() throws Exception {
        paper = createPaper();
        PaymentSubmission physicalStudent = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-A", "Student A");
        PaymentSubmission onlineStudent = createSubmission(PaperWritingMode.ONLINE, null, "STU-B", "Student B");

        createSlot(paper, physicalStudent, null);
        createSlot(paper, onlineStudent, null);

        mockMvc.perform(get("/papers/{paperId}/attendance/students", paper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].codeNumber").value("STU-A"));
    }

    @Test
    @DisplayName("GET .../attendance/students - centerId=UNASSIGNED returns only the 'not specified' bucket")
    void getStudents_unassignedCenterId_returnsNotSpecifiedBucketOnly() throws Exception {
        paper = createPaper();
        PaymentSubmission withCenter = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-A", "Student A");
        PaymentSubmission withoutCenter = createSubmission(PaperWritingMode.PHYSICAL, null, "STU-B", "Student B");

        createSlot(paper, withCenter, null);
        createSlot(paper, withoutCenter, null);

        mockMvc.perform(get("/papers/{paperId}/attendance/students", paper.getId())
                        .param("centerId", "UNASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].codeNumber").value("STU-B"))
                .andExpect(jsonPath("$.items[0].paperCenterId").value("UNASSIGNED"))
                .andExpect(jsonPath("$.items[0].paperCenterName").value("Not specified"));
    }

    @Test
    @DisplayName("GET .../attendance/students - attended filter narrows to only attended or only absent students")
    void getStudents_attendedFilter() throws Exception {
        paper = createPaper();
        PaymentSubmission attended = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-A", "Student A");
        PaymentSubmission absent = createSubmission(PaperWritingMode.PHYSICAL, COLOMBO_ID, "STU-B", "Student B");

        createSlot(paper, attended, LocalDateTime.now());
        createSlot(paper, absent, null);

        mockMvc.perform(get("/papers/{paperId}/attendance/students", paper.getId())
                        .param("attended", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].codeNumber").value("STU-A"));

        mockMvc.perform(get("/papers/{paperId}/attendance/students", paper.getId())
                        .param("attended", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].codeNumber").value("STU-B"));
    }

    @Test
    @DisplayName("GET .../attendance/students - 404s for a non-existent paper")
    void getStudents_paperNotFound() throws Exception {
        mockMvc.perform(get("/papers/{paperId}/attendance/students", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

}
