package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.paper.PaperCenterAttendanceStudentDto;
import dopaminelite.payment_portal.dto.paper.PaperCenterAttendanceSummaryResponse;
import dopaminelite.payment_portal.service.PaperAttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for a paper's attendance breakdown by physical paper center - backs the
 * admin/instructor "Paper Center Attendance" dashboard. Read-only: attendance itself is still
 * recorded exclusively via {@code PaperSlotController}'s scan/consume flow.
 */
@RestController
@RequestMapping("/papers/{paperId}/attendance")
@RequiredArgsConstructor
public class PaperAttendanceController {

    private final PaperAttendanceService paperAttendanceService;

    /**
     * One row per active paper center (plus grand totals) showing how many slots were opened
     * and attended for this paper.
     *
     * @param paperId the paper's ID
     * @return the per-center breakdown and totals
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID
     */
    @GetMapping("/by-center")
    public ResponseEntity<PaperCenterAttendanceSummaryResponse> getByCenterSummary(@PathVariable UUID paperId) {
        return ResponseEntity.ok(paperAttendanceService.getByCenterSummary(paperId));
    }

    /**
     * The dashboard's "Students" tab: every student with a slot for this paper, optionally
     * filtered by center and/or attendance. A high {@code limit} (up to 5000) is intentionally
     * allowed so the frontend's CSV export can fetch a filtered set in one request rather than
     * paging through it.
     *
     * @param paperId the paper's ID
     * @param centerId omit for no center filter, {@code "UNASSIGNED"} for students with no
     *        center recorded, or a specific row's {@code paperCenterId} otherwise
     * @param attended omit for no attendance filter, true/false to match attended/absent only
     * @param limit maximum number of results per page, defaults to 50
     * @param offset number of results to skip, defaults to 0
     * @return paginated student rows matching the filters
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID
     */
    @GetMapping("/students")
    public ResponseEntity<PaginatedResponse<PaperCenterAttendanceStudentDto>> getStudents(
            @PathVariable UUID paperId,
            @RequestParam(required = false) String centerId,
            @RequestParam(required = false) Boolean attended,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        if (limit < 1 || limit > 5000) {
            limit = 50;
        }

        PaginatedResponse<PaperCenterAttendanceStudentDto> response =
                paperAttendanceService.getStudents(paperId, centerId, attended, limit, offset);
        return ResponseEntity.ok(response);
    }

}
