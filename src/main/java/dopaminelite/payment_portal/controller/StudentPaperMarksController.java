package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.paper.PaperMarkResponse;
import dopaminelite.payment_portal.service.PaperMarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for a student's own marks across every paper — a cross-paper counterpart to
 * {@link PaperMarkController}, which is scoped to one paper at a time. Backs the BFF's unified
 * academic profile aggregation.
 */
@RestController
@RequestMapping("/paper-marks")
@RequiredArgsConstructor
public class StudentPaperMarksController {

    private final PaperMarkService paperMarkService;

    /**
     * Lists every mark recorded for a student, across all papers.
     *
     * @param studentId the student's ID
     * @return the student's marks, most recent paper first; an empty list if they have none yet
     */
    @GetMapping
    public ResponseEntity<List<PaperMarkResponse>> listMarksForStudent(@RequestParam UUID studentId) {
        return ResponseEntity.ok(paperMarkService.listMarksForStudent(studentId));
    }

}
