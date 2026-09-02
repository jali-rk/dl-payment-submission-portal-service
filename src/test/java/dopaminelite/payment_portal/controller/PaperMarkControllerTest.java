package dopaminelite.payment_portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dopaminelite.payment_portal.dto.external.StudentLookupDto;
import dopaminelite.payment_portal.dto.paper.MarkSchemeDto;
import dopaminelite.payment_portal.dto.paper.PaperMarkCreateRequest;
import dopaminelite.payment_portal.dto.paper.PaperMarkUpdateRequest;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.repository.PaperMarkRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
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
 * Controller test for instructor-entered paper marks: student verification, bounds/section
 * validation, duplicate rejection, and the mark-scheme lock/unlock lifecycle. Mirrors
 * {@link PaymentPortalControllerTest}'s style (real H2-backed {@code @SpringBootTest}, rolled
 * back per test via {@code @Transactional}), except {@link StudentLookupService} is stubbed via
 * {@code @MockitoBean} since it calls the BFF over HTTP, which isn't available in this test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Paper Mark API Tests")
class PaperMarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperMarkRepository paperMarkRepository;

    @MockitoBean
    private StudentLookupService studentLookupService;

    private static final UUID KNOWN_STUDENT_ID = UUID.randomUUID();
    private static final String KNOWN_CODE_NUMBER = "STU-001";

    @BeforeEach
    void setUp() {
        paperMarkRepository.deleteAll();
        paperRepository.deleteAll();

        StudentLookupDto knownStudent = new StudentLookupDto(
                KNOWN_STUDENT_ID, "Test Student", "student@example.com", "0770000000", KNOWN_CODE_NUMBER);
        org.mockito.Mockito.when(studentLookupService.findByCodeNumber(KNOWN_CODE_NUMBER))
                .thenReturn(Optional.of(knownStudent));
        org.mockito.Mockito.when(studentLookupService.findByCodeNumber("NO-SUCH-STUDENT"))
                .thenReturn(Optional.empty());
    }

    private Paper createPaperWithScheme(BigDecimal mcqMax, BigDecimal structuredMax, BigDecimal essayMax) {
        Paper paper = new Paper();
        paper.setTitle("Test Paper");
        paper.setStartDate(LocalDate.now().minusDays(1));
        paper.setEndDate(LocalDate.now().plusDays(1));
        paper.setMcqMaxMarks(mcqMax);
        paper.setStructuredMaxMarks(structuredMax);
        paper.setEssayMaxMarks(essayMax);
        return paperRepository.save(paper);
    }

    private PaperMarkCreateRequest validRequest() {
        PaperMarkCreateRequest request = new PaperMarkCreateRequest();
        request.setStudentCodeNumber(KNOWN_CODE_NUMBER);
        request.setMcqMarks(new BigDecimal("33.333"));
        request.setStructuredMarks(new BigDecimal("50.000"));
        request.setTotalMarks(new BigDecimal("83.333"));
        return request;
    }

    @Test
    @DisplayName("POST /papers/{paperId}/marks - Should create a mark successfully")
    void testCreateMark_Success() throws Exception {
        Paper paper = createPaperWithScheme(new BigDecimal("40.000"), new BigDecimal("60.000"), null);

        mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.studentId").value(KNOWN_STUDENT_ID.toString()))
                .andExpect(jsonPath("$.student.codeNumber").value(KNOWN_CODE_NUMBER))
                .andExpect(jsonPath("$.mcqMarks").value(33.333))
                .andExpect(jsonPath("$.totalMarks").value(83.333));
    }

    @Test
    @DisplayName("POST /papers/{paperId}/marks - Should reject when paper has no mark scheme configured")
    void testCreateMark_NoSchemeConfigured() throws Exception {
        Paper paper = createPaperWithScheme(null, null, null);

        mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /papers/{paperId}/marks - Should reject a mark for a disabled section")
    void testCreateMark_DisabledSection() throws Exception {
        Paper paper = createPaperWithScheme(new BigDecimal("40.000"), new BigDecimal("60.000"), null);

        PaperMarkCreateRequest request = validRequest();
        request.setEssayMarks(new BigDecimal("10.000")); // essay isn't enabled on this paper

        mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("Essay")));
    }

    @Test
    @DisplayName("POST /papers/{paperId}/marks - Should reject a section mark above its configured max")
    void testCreateMark_ExceedsSectionMax() throws Exception {
        Paper paper = createPaperWithScheme(new BigDecimal("40.000"), new BigDecimal("60.000"), null);

        PaperMarkCreateRequest request = validRequest();
        request.setMcqMarks(new BigDecimal("50.000")); // exceeds the 40 max

        mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /papers/{paperId}/marks - Should 404 when the student code number doesn't resolve")
    void testCreateMark_StudentNotFound() throws Exception {
        Paper paper = createPaperWithScheme(new BigDecimal("40.000"), new BigDecimal("60.000"), null);

        PaperMarkCreateRequest request = validRequest();
        request.setStudentCodeNumber("NO-SUCH-STUDENT");

        mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /papers/{paperId}/marks - Should reject a duplicate mark for the same student")
    void testCreateMark_Duplicate() throws Exception {
        Paper paper = createPaperWithScheme(new BigDecimal("40.000"), new BigDecimal("60.000"), null);

        mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("GET /papers/{paperId}/marks/verify-student - Should report student identity and existing-mark status")
    void testVerifyStudent_Success() throws Exception {
        Paper paper = createPaperWithScheme(new BigDecimal("40.000"), new BigDecimal("60.000"), null);

        mockMvc.perform(get("/papers/{paperId}/marks/verify-student", paper.getId())
                        .param("studentCodeNumber", KNOWN_CODE_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(KNOWN_STUDENT_ID.toString()))
                .andExpect(jsonPath("$.alreadyHasMark").value(false));

        mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/papers/{paperId}/marks/verify-student", paper.getId())
                        .param("studentCodeNumber", KNOWN_CODE_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyHasMark").value(true));
    }

    @Test
    @DisplayName("PATCH /papers/{paperId}/marks/{markId} - Should update only the provided fields")
    void testUpdateMark_Success() throws Exception {
        Paper paper = createPaperWithScheme(new BigDecimal("40.000"), new BigDecimal("60.000"), null);

        String createResponse = mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID markId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        PaperMarkUpdateRequest update = new PaperMarkUpdateRequest();
        update.setTotalMarks(new BigDecimal("90.000"));

        mockMvc.perform(patch("/papers/{paperId}/marks/{markId}", paper.getId(), markId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMarks").value(90.000))
                .andExpect(jsonPath("$.mcqMarks").value(33.333)); // untouched field preserved
    }

    @Test
    @DisplayName("DELETE then PUT mark-scheme - Deleting the last mark unlocks the paper's scheme")
    void testDeleteMark_UnlocksMarkScheme() throws Exception {
        Paper paper = createPaperWithScheme(new BigDecimal("40.000"), new BigDecimal("60.000"), null);

        String createResponse = mockMvc.perform(post("/papers/{paperId}/marks", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID markId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        MarkSchemeDto newScheme = new MarkSchemeDto(new BigDecimal("30.000"), new BigDecimal("70.000"), null);

        // Locked while the mark exists
        mockMvc.perform(put("/papers/{paperId}/mark-scheme", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newScheme)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(delete("/papers/{paperId}/marks/{markId}", paper.getId(), markId))
                .andExpect(status().isNoContent());

        // Unlocked once the only mark is gone
        mockMvc.perform(put("/papers/{paperId}/mark-scheme", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newScheme)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markScheme.mcqMaxMarks").value(30.000));
    }

}
