package dopaminelite.payment_portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dopaminelite.payment_portal.dto.external.StudentLookupDto;
import dopaminelite.payment_portal.dto.paper.CorrelationCreateRequest;
import dopaminelite.payment_portal.dto.paper.CorrelationUpdateRequest;
import dopaminelite.payment_portal.dto.paper.MarkSchemeDto;
import dopaminelite.payment_portal.dto.paper.PaperMarkCreateRequest;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperCorrelation;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import dopaminelite.payment_portal.repository.PaperCorrelationRepository;
import dopaminelite.payment_portal.repository.PaperMarkRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import dopaminelite.payment_portal.service.StudentLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers correlations as the admin meets them: creating and renaming them, the rules about when a
 * paper-event may join one, and the consequence that matters most — that the two sittings of one
 * paper share a single set of marks and a single mark scheme.
 *
 * <p>Same shape as {@link PaperMarkControllerTest}: a real H2-backed {@code @SpringBootTest} rolled
 * back per test, with {@link StudentLookupService} stubbed since it calls the BFF over HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Paper Correlation API Tests")
class PaperCorrelationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperCorrelationRepository correlationRepository;

    @Autowired
    private PaperMarkRepository paperMarkRepository;

    @Autowired
    private PaymentPortalRepository portalRepository;

    @MockitoBean
    private StudentLookupService studentLookupService;

    private static final UUID KNOWN_STUDENT_ID = UUID.randomUUID();
    private static final String KNOWN_CODE_NUMBER = "STU-001";

    @BeforeEach
    void setUp() {
        paperMarkRepository.deleteAll();
        paperRepository.deleteAll();
        correlationRepository.deleteAll();

        StudentLookupDto student = new StudentLookupDto(
                KNOWN_STUDENT_ID, KNOWN_CODE_NUMBER, "Test Student",
                "student@example.com", "0770000000");
        org.mockito.Mockito.when(studentLookupService.findByCodeNumber(KNOWN_CODE_NUMBER))
                .thenReturn(Optional.of(student));
    }

    @Test
    @DisplayName("a code is uppercased on save, so case can never split one paper into two")
    void createCorrelation_uppercasesCode() throws Exception {
        mockMvc.perform(post("/correlations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CorrelationCreateRequest("oct-w1", "October Week 1 Paper"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OCT-W1"))
                .andExpect(jsonPath("$.displayName").value("October Week 1 Paper"));
    }

    @Test
    @DisplayName("a code containing a space is rejected")
    void createCorrelation_rejectsSpaces() throws Exception {
        mockMvc.perform(post("/correlations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CorrelationCreateRequest("OCT W1", "October Week 1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not valid")));
    }

    @Test
    @DisplayName("two correlations can't share a code, however it was typed")
    void createCorrelation_rejectsDuplicateCode() throws Exception {
        correlation("OCT-W1");

        mockMvc.perform(post("/correlations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CorrelationCreateRequest("oct-w1", "Another"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("renaming is allowed at any time, since paper-events are grouped by id not by code")
    void updateCorrelation_renameAllowed() throws Exception {
        PaperCorrelation correlation = correlation("OCT-W1");
        paper("Early access", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), correlation);

        mockMvc.perform(patch("/correlations/" + correlation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CorrelationUpdateRequest("OCT-WEEK1", "October Week One"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OCT-WEEK1"))
                .andExpect(jsonPath("$.displayName").value("October Week One"));
    }

    @Test
    @DisplayName("a correlation with paper-events still attached can't be deleted")
    void deleteCorrelation_blockedWhileInUse() throws Exception {
        PaperCorrelation correlation = correlation("OCT-W1");
        paper("Early access", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), correlation);

        mockMvc.perform(delete("/correlations/" + correlation.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("paper-events still belong to it")));
    }

    @Test
    @DisplayName("an unused correlation can be deleted")
    void deleteCorrelation_allowedWhenUnused() throws Exception {
        PaperCorrelation correlation = correlation("OCT-W1");

        mockMvc.perform(delete("/correlations/" + correlation.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a paper-event that has already started can't be moved into a correlation")
    void attach_blockedOnceStarted() throws Exception {
        PaperCorrelation correlation = correlation("OCT-W1");
        Paper started = paper("Running paper", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), null);

        mockMvc.perform(patch("/papers/" + started.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correlationId\":\"" + correlation.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already started")));
    }

    @Test
    @DisplayName("a paper-event with marks of its own can't be moved into a correlation")
    void attach_blockedWhenItHasOwnMarks() throws Exception {
        PaperCorrelation correlation = correlation("OCT-W1");
        Paper standalone = paper("Future paper", LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null);
        setMarkScheme(standalone, new BigDecimal("40"));
        addMark(standalone);

        mockMvc.perform(patch("/papers/" + standalone.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correlationId\":\"" + correlation.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("marks of its own")));
    }

    @Test
    @DisplayName("both sittings of one paper share a single set of marks")
    void marksAreSharedAcrossSittings() throws Exception {
        PaperCorrelation correlation = correlation("OCT-W1");
        Paper earlyAccess = paper("Early access", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), correlation);
        Paper fullWeek = paper("Full week", LocalDate.now().minusDays(1), LocalDate.now().plusDays(3), correlation);
        setCorrelationMarkScheme(correlation, new BigDecimal("40"));

        // Entered against one sitting...
        addMark(earlyAccess);

        // ...and visible through the other, because the marks belong to the paper, not the sitting.
        mockMvc.perform(get("/papers/" + fullWeek.getId() + "/marks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].studentId").value(KNOWN_STUDENT_ID.toString()));
    }

    @Test
    @DisplayName("the mark scheme lock can't be sidestepped through the other sitting")
    void markSchemeLock_notBypassableViaSibling() throws Exception {
        PaperCorrelation correlation = correlation("OCT-W1");
        Paper earlyAccess = paper("Early access", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), correlation);
        Paper fullWeek = paper("Full week", LocalDate.now().minusDays(1), LocalDate.now().plusDays(3), correlation);
        setCorrelationMarkScheme(correlation, new BigDecimal("40"));
        addMark(earlyAccess);

        // fullWeek has no marks of its own, but the marks it shares are what lock the scheme.
        mockMvc.perform(put("/papers/" + fullWeek.getId() + "/mark-scheme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MarkSchemeDto(new BigDecimal("50"), null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("mark(s) already exist")));
    }

    @Test
    @DisplayName("joining with the same scheme written at a different scale is accepted")
    void joiningWithSameSchemeAtDifferentScale_isAccepted() throws Exception {
        PaperCorrelation correlation = correlation("OCT-W1");
        // Stored the way the numeric(9,3) column hands it back.
        correlation.setMcqMaxMarks(new BigDecimal("40.000"));
        correlationRepository.saveAndFlush(correlation);

        // Sent the way an admin types it - and the way Duplicate prefills it.
        mockMvc.perform(post("/papers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"title":"Second sitting","startDate":"%s","endDate":"%s",
                                 "linkedPortalIds":["%s"],"correlationId":"%s",
                                 "markScheme":{"mcqMaxMarks":40}}""",
                                LocalDate.now().plusDays(7), LocalDate.now().plusDays(11),
                                portal().getId(), correlation.getId())))
                .andExpect(status().isCreated());
    }

    // --- fixtures ---------------------------------------------------------------------------

    private PaymentPortal portal() {
        PaymentPortal portal = new PaymentPortal();
        portal.setMonth(9);
        portal.setYear(2030);
        portal.setName("scale-test-" + UUID.randomUUID());
        portal.setDisplayName("Scale Test Portal");
        portal.setIsPublished(true);
        portal.setVisibility(PortalVisibility.PUBLISHED);
        return portalRepository.saveAndFlush(portal);
    }

    private PaperCorrelation correlation(String code) {
        PaperCorrelation correlation = new PaperCorrelation();
        correlation.setCode(code);
        correlation.setDisplayName("Paper " + code);
        return correlationRepository.saveAndFlush(correlation);
    }

    private Paper paper(String title, LocalDate start, LocalDate end, PaperCorrelation correlation) {
        Paper paper = new Paper();
        paper.setTitle(title);
        paper.setStartDate(start);
        paper.setEndDate(end);
        paper.setCorrelation(correlation);
        return paperRepository.saveAndFlush(paper);
    }

    private void setMarkScheme(Paper paper, BigDecimal mcqMax) {
        paper.setMcqMaxMarks(mcqMax);
        paperRepository.saveAndFlush(paper);
    }

    private void setCorrelationMarkScheme(PaperCorrelation correlation, BigDecimal mcqMax) {
        correlation.setMcqMaxMarks(mcqMax);
        correlationRepository.saveAndFlush(correlation);
    }

    private void addMark(Paper paper) throws Exception {
        PaperMarkCreateRequest request = new PaperMarkCreateRequest();
        request.setStudentCodeNumber(KNOWN_CODE_NUMBER);
        request.setMcqMarks(new BigDecimal("30"));
        request.setTotalMarks(new BigDecimal("75"));

        mockMvc.perform(post("/papers/" + paper.getId() + "/marks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

}
