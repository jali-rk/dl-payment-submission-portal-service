package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.admin.SubmissionExportRequest;
import dopaminelite.payment_portal.entity.enums.StudyMedium;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.service.AdminExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * REST controller for admin payment submission exports.
 * Provides endpoint for exporting submissions to XLSX format.
 */
@Slf4j
@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class AdminPaymentSubmissionExportController {

    private final AdminExportService exportService;

    /**
     * Exports payment submissions to XLSX format based on filters and selected columns.
     *
     * @param studentId filter by student ID, optional
     * @param portalId filter by portal ID, optional
     * @param status filter by submission status, optional
     * @param month filter by portal's month (1-12), optional
     * @param year filter by portal's year, optional
     * @param studyMedium filter by student's study medium, optional
     * @param paperCenter filter by paper center ID, optional
     * @param submittedAtFrom filter submissions from this datetime (inclusive), optional
     * @param submittedAtTo filter submissions until this datetime (inclusive), optional
     * @param request the export request containing selected columns
     * @return XLSX file as byte array
     * @throws IOException if an error occurs during file generation
     */
    @PostMapping("/export")
    public ResponseEntity<byte[]> exportSubmissions(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID portalId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) StudyMedium studyMedium,
            @RequestParam(required = false) String paperCenter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime submittedAtFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime submittedAtTo,
            @Valid @RequestBody SubmissionExportRequest request
    ) throws IOException {
        log.info("Received export request with filters - portalId: {}, status: {}, studyMedium: {}, paperCenter: {}, columns: {}",
                portalId, status, studyMedium, paperCenter, request.getColumns());

        // Convert OffsetDateTime to LocalDateTime using Asia/Colombo timezone
        ZoneId sriLankaZone = ZoneId.of("Asia/Colombo");
        LocalDateTime fromDate = null;
        if (submittedAtFrom != null) {
            fromDate = submittedAtFrom.atZoneSameInstant(sriLankaZone).toLocalDateTime();
        }

        LocalDateTime toDate = null;
        if (submittedAtTo != null) {
            toDate = submittedAtTo.atZoneSameInstant(sriLankaZone).toLocalDateTime();
        }

        byte[] excelData = exportService.exportSubmissions(
                studentId, portalId, status, month, year, studyMedium, paperCenter, fromDate, toDate, request.getColumns()
        );

        // Generate filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "payment_submissions_" + timestamp + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(excelData.length);

        log.info("Successfully generated export file: {} ({} bytes)", filename, excelData.length);
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }
}
