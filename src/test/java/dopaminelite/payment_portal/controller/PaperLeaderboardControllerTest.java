package dopaminelite.payment_portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dopaminelite.payment_portal.dto.external.StudentLookupDto;
import dopaminelite.payment_portal.dto.paper.PaperMarkCreateRequest;
import dopaminelite.payment_portal.dto.paper.PaperMarkUpdateRequest;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.repository.PaperMarkRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
import dopaminelite.payment_portal.service.StudentLookupService;
import dopaminelite.payment_portal.util.JwtUserIdExtractor;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller test for a paper's leaderboard: rank generation (including standard competition
 * ranking on ties), the generate-before-publish guard, and role-based read visibility. Mirrors
 * {@link PaperMarkControllerTest}'s style (real H2-backed {@code @SpringBootTest}, rolled back
 * per test via {@code @Transactional}, {@link StudentLookupService} stubbed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Paper Leaderboard API Tests")
class PaperLeaderboardControllerTest {

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

    private static final UUID STUDENT_A_ID = UUID.randomUUID();
    private static final UUID STUDENT_B_ID = UUID.randomUUID();
    private static final UUID STUDENT_C_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        paperMarkRepository.deleteAll();
        paperRepository.deleteAll();

        stubStudent(STUDENT_A_ID, "STU-A", "Student A");
        stubStudent(STUDENT_B_ID, "STU-B", "Student B");
        stubStudent(STUDENT_C_ID, "STU-C", "Student C");
    }

    private void stubStudent(UUID id, String codeNumber, String name) {
        StudentLookupDto dto = new StudentLookupDto(id, name, name.toLowerCase() + "@example.com", "0770000000", codeNumber);
        org.mockito.Mockito.when(studentLookupService.findByCodeNumber(codeNumber)).thenReturn(Optional.of(dto));
    }

    private Paper createPaperWithScheme() {
        Paper paper = new Paper();
        paper.setTitle("Leaderboard Test Paper");
        paper.setStartDate(LocalDate.now().minusDays(1));
        paper.setEndDate(LocalDate.now().plusDays(1));
        paper.setMcqMaxMarks(new BigDecimal("40.000"));
        paper.setStructuredMaxMarks(new BigDecimal("60.000"));
        return paperRepository.save(paper);
    }

    private void createMark(UUID paperId, String codeNumber, String totalMarks) throws Exception {
        PaperMarkCreateRequest request = new PaperMarkCreateRequest();
        request.setStudentCodeNumber(codeNumber);
        request.setMcqMarks(new BigDecimal("30.000"));
        request.setStructuredMarks(new BigDecimal("40.000"));
        request.setTotalMarks(new BigDecimal(totalMarks));

        mockMvc.perform(post("/papers/{paperId}/marks", paperId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /papers/{paperId}/leaderboard/generate - Rejects when no marks exist yet")
    void testGenerateRanks_NoMarks() throws Exception {
        Paper paper = createPaperWithScheme();

        mockMvc.perform(post("/papers/{paperId}/leaderboard/generate", paper.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /papers/{paperId}/leaderboard/generate - Standard competition ranking: ties share a rank, next rank skips")
    void testGenerateRanks_TieHandling() throws Exception {
        Paper paper = createPaperWithScheme();
        createMark(paper.getId(), "STU-A", "85.000");
        createMark(paper.getId(), "STU-B", "85.000"); // ties with A
        createMark(paper.getId(), "STU-C", "70.000");

        mockMvc.perform(post("/papers/{paperId}/leaderboard/generate", paper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[1].rank").value(1))
                .andExpect(jsonPath("$.entries[2].rank").value(3)) // rank 2 is skipped
                .andExpect(jsonPath("$.entries[2].codeNumber").value("STU-C"))
                .andExpect(jsonPath("$.lastGeneratedAt").exists());
    }

    @Test
    @DisplayName("PATCH .../visibility - Rejects publishing before ranks have ever been generated")
    void testPublish_BeforeGenerate_Rejected() throws Exception {
        Paper paper = createPaperWithScheme();
        createMark(paper.getId(), "STU-A", "85.000");

        mockMvc.perform(patch("/papers/{paperId}/leaderboard/visibility", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"published\": true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET .../leaderboard - Hidden from an unprivileged/absent role until published, 404s not 403")
    void testGetLeaderboard_HiddenUntilPublished() throws Exception {
        Paper paper = createPaperWithScheme();
        createMark(paper.getId(), "STU-A", "85.000");

        // No role param (e.g. a STUDENT caller) - not published yet.
        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Privileged roles can always see it, hidden or not.
        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId()).param("role", "INSTRUCTOR"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId()).param("role", "ADMIN"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId()).param("role", "MAIN_ADMIN"))
                .andExpect(status().isOk());

        // Generate then publish - now a STUDENT (or absent role) can see it too.
        mockMvc.perform(post("/papers/{paperId}/leaderboard/generate", paper.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/papers/{paperId}/leaderboard/visibility", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"published\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId()).param("role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
    }

    @Test
    @DisplayName("Editing a mark after generation doesn't recompute its rank until Generate Ranks is pressed again")
    void testEditMark_DoesNotRecomputeRankUntilRegenerated() throws Exception {
        Paper paper = createPaperWithScheme();
        createMark(paper.getId(), "STU-A", "85.000");
        createMark(paper.getId(), "STU-B", "70.000");

        mockMvc.perform(post("/papers/{paperId}/leaderboard/generate", paper.getId()))
                .andExpect(status().isOk());
        UUID markAId = UUID.fromString(findMarkId(paper.getId(), "STU-A"));

        // Edit A's total down below B's - rank should NOT recompute automatically.
        PaperMarkUpdateRequest update = new PaperMarkUpdateRequest();
        update.setTotalMarks(new BigDecimal("50.000"));
        mockMvc.perform(patch("/papers/{paperId}/marks/{markId}", paper.getId(), markAId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        // Leaderboard still shows A ranked 1st (stale) even though A's live total is now lower.
        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId()).param("role", "MAIN_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].codeNumber").value("STU-A"))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].totalMarks").value(50.000));

        // Regenerating fixes it.
        mockMvc.perform(post("/papers/{paperId}/leaderboard/generate", paper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].codeNumber").value("STU-B"))
                .andExpect(jsonPath("$.entries[1].codeNumber").value("STU-A"));
    }

    @Test
    @DisplayName("GET .../leaderboard - Pagination: limit/offset page the entries, total reflects the full ranked count")
    void testGetLeaderboard_Pagination() throws Exception {
        Paper paper = createPaperWithScheme();
        createMark(paper.getId(), "STU-A", "90.000");
        createMark(paper.getId(), "STU-B", "80.000");
        createMark(paper.getId(), "STU-C", "70.000");

        mockMvc.perform(post("/papers/{paperId}/leaderboard/generate", paper.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId())
                        .param("role", "MAIN_ADMIN").param("limit", "2").param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.entries[0].codeNumber").value("STU-A"))
                .andExpect(jsonPath("$.entries[1].codeNumber").value("STU-B"))
                .andExpect(jsonPath("$.total").value(3));

        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId())
                        .param("role", "MAIN_ADMIN").param("limit", "2").param("offset", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.entries[0].codeNumber").value("STU-C"))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("GET .../leaderboard - callerEntry surfaces the caller's own rank regardless of which page was requested")
    void testGetLeaderboard_CallerEntry() throws Exception {
        Paper paper = createPaperWithScheme();
        createMark(paper.getId(), "STU-A", "90.000");
        createMark(paper.getId(), "STU-B", "80.000");
        createMark(paper.getId(), "STU-C", "70.000");

        mockMvc.perform(post("/papers/{paperId}/leaderboard/generate", paper.getId()))
                .andExpect(status().isOk());

        // Page 1 with limit=1 would only ever contain STU-A - but STUDENT_C is the caller here,
        // and their entry must still show up via callerEntry, not require paging to find it.
        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId())
                        .param("role", "STUDENT").param("limit", "1").param("offset", "0")
                        .header("Authorization", bearerTokenFor(STUDENT_C_ID)))
                .andExpect(status().isNotFound()); // not published yet

        mockMvc.perform(patch("/papers/{paperId}/leaderboard/visibility", paper.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"published\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/papers/{paperId}/leaderboard", paper.getId())
                        .param("role", "STUDENT").param("limit", "1").param("offset", "0")
                        .header("Authorization", bearerTokenFor(STUDENT_C_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].codeNumber").value("STU-A")) // page content unaffected
                .andExpect(jsonPath("$.callerEntry.codeNumber").value("STU-C"))
                .andExpect(jsonPath("$.callerEntry.rank").value(3));
    }

    /** Builds an unsigned test JWT carrying only the {@code id} claim {@link JwtUserIdExtractor} reads. */
    private String bearerTokenFor(UUID userId) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"id\":\"" + userId + "\"}").getBytes(StandardCharsets.UTF_8));
        return "Bearer " + header + "." + payload + ".";
    }

    private String findMarkId(UUID paperId, String codeNumber) throws Exception {
        String listResponse = mockMvc.perform(get("/papers/{paperId}/marks", paperId).param("limit", "50"))
                .andReturn().getResponse().getContentAsString();
        var items = objectMapper.readTree(listResponse).get("items");
        for (var item : items) {
            if (item.get("student").get("codeNumber").asText().equals(codeNumber)) {
                return item.get("id").asText();
            }
        }
        throw new IllegalStateException("Mark not found for " + codeNumber);
    }

}
