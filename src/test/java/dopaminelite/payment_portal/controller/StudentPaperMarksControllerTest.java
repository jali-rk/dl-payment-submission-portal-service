package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.entity.MarkStudentSnapshot;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperMark;
import dopaminelite.payment_portal.repository.PaperMarkRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller test for the student-scoped, cross-paper marks query that backs the unified
 * academic profile. Mirrors {@link PaperMarkControllerTest}'s style (real H2-backed
 * {@code @SpringBootTest}, rolled back per test via {@code @Transactional}) — no mocked
 * dependencies needed here since this endpoint never calls out to the BFF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Student Paper Marks API Tests")
class StudentPaperMarksControllerTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperMarkRepository paperMarkRepository;

    private UUID studentId;

    @BeforeEach
    void setUp() {
        paperMarkRepository.deleteAll();
        paperRepository.deleteAll();
        studentId = UUID.randomUUID();
    }

    private Paper createPaper(String title, LocalDate startDate) {
        Paper paper = new Paper();
        paper.setTitle(title);
        paper.setStartDate(startDate);
        paper.setEndDate(startDate);
        paper.setMcqMaxMarks(new BigDecimal("40.000"));
        paper.setStructuredMaxMarks(new BigDecimal("60.000"));
        return paperRepository.save(paper);
    }

    private void createMark(Paper paper, UUID studentId, BigDecimal totalMarks, Integer rank) {
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
        mark.setTotalMarks(totalMarks);
        mark.setRank(rank);
        mark.setEnteredByInstructorId(UUID.randomUUID());
        mark.setLastUpdatedByInstructorId(UUID.randomUUID());
        paperMarkRepository.save(mark);
    }

    @Test
    @DisplayName("GET /paper-marks?studentId= - Returns the student's marks across every paper, including rank, most recent paper first")
    void listMarksForStudent_returnsMarksAcrossPapers() throws Exception {
        Paper paper1 = createPaper("Paper 01", LocalDate.now().minusDays(30));
        Paper paper2 = createPaper("Paper 02", LocalDate.now().minusDays(1));
        createMark(paper1, studentId, new BigDecimal("83.333"), 5);
        createMark(paper2, studentId, new BigDecimal("90.000"), null); // ranks not generated yet
        createMark(paper1, UUID.randomUUID(), new BigDecimal("70.000"), 12); // a different student

        mockMvc.perform(get("/paper-marks").param("studentId", studentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].paperId").value(paper2.getId().toString()))
                .andExpect(jsonPath("$[0].rank").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].paperId").value(paper1.getId().toString()))
                .andExpect(jsonPath("$[1].rank").value(5));
    }

    @Test
    @DisplayName("GET /paper-marks?studentId= - Returns an empty list, not an error, for a student with no marks")
    void listMarksForStudent_emptyForStudentWithNoMarks() throws Exception {
        mockMvc.perform(get("/paper-marks").param("studentId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

}
