package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.enums.StudyMedium;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for exporting payment submissions to XLSX format.
 * Handles filtering, column selection, and paper center name lookup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentSubmissionRepository submissionRepository;
    private final PaperCenterService paperCenterService;

    /**
     * Exports payment submissions to XLSX format based on filters and selected columns.
     *
     * @param studentId filter by student ID, null for no filtering
     * @param portalId filter by portal ID, null for no filtering
     * @param status filter by submission status, null for no filtering
     * @param month filter by portal's month (1-12), null for no filtering
     * @param year filter by portal's year, null for no filtering
     * @param studyMedium filter by student's study medium, null for no filtering
     * @param paperCenterId filter by paper center ID, null for no filtering
     * @param fromDate filter submissions from this date (inclusive), null for no filtering
     * @param toDate filter submissions until this date (inclusive), null for no filtering
     * @param columns list of column names to include in the export
     * @return byte array containing the XLSX file
     * @throws IOException if an error occurs during file generation
     */
    public byte[] exportSubmissions(
            UUID studentId,
            UUID portalId,
            SubmissionStatus status,
            Integer month,
            Integer year,
            StudyMedium studyMedium,
            String paperCenterId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            List<String> columns
    ) throws IOException {
        log.info("Exporting submissions with filters - portalId: {}, status: {}, studyMedium: {}, paperCenterId: {}, columns: {}",
                portalId, status, studyMedium, paperCenterId, columns);

        // Fetch all matching submissions (up to a reasonable limit)
        Pageable pageable = PageRequest.of(0, 1000); // Limit to 1000 records for safety
        Page<PaymentSubmission> submissionPage = submissionRepository.findByAdminFilters(
                studentId, portalId, status, month, year, studyMedium, paperCenterId, fromDate, toDate, pageable
        );

        List<PaymentSubmission> submissions = submissionPage.getContent();
        log.info("Found {} submissions to export", submissions.size());

        // Fetch paper center name map
        Map<String, String> paperCenterNameMap = paperCenterService.getPaperCenterNameMap();

        // Generate XLSX
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Payment Submissions");

            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderCellStyle(workbook);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            int rowNum = 1;
            for (PaymentSubmission submission : submissions) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < columns.size(); i++) {
                    Cell cell = row.createCell(i);
                    String value = getCellValue(submission, columns.get(i), paperCenterNameMap);
                    cell.setCellValue(value != null ? value : "");
                }
            }

            // Auto-size columns
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            log.info("Successfully generated XLSX with {} rows", submissions.size());
            return outputStream.toByteArray();
        }
    }

    /**
     * Creates a styled header cell for the XLSX file.
     */
    private CellStyle createHeaderCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * Gets the cell value for a specific column from a submission.
     */
    private String getCellValue(PaymentSubmission submission, String columnName, Map<String, String> paperCenterNameMap) {
        return switch (columnName) {
            case "Student Name" -> submission.getStudentSnapshot() != null ? submission.getStudentSnapshot().getFullName() : null;
            case "Student ID" -> submission.getStudentSnapshot() != null ? submission.getStudentSnapshot().getCodeNumber() : null;
            case "Email" -> submission.getStudentSnapshot() != null ? submission.getStudentSnapshot().getEmail() : null;
            case "WhatsApp Number" -> submission.getStudentSnapshot() != null ? submission.getStudentSnapshot().getWhatsappNumber() : null;
            case "Secondary Phone" -> submission.getStudentSnapshot() != null ? submission.getStudentSnapshot().getSecondaryPhoneNumber() : null;
            case "Address" -> submission.getStudentSnapshot() != null ? submission.getStudentSnapshot().getAddress() : null;
            case "NIC" -> submission.getStudentSnapshot() != null ? submission.getStudentSnapshot().getNic() : null;
            case "School" -> submission.getStudentSnapshot() != null ? submission.getStudentSnapshot().getSchool() : null;
            case "Study Medium" -> submission.getStudentSnapshot() != null && submission.getStudentSnapshot().getStudyMedium() != null
                    ? submission.getStudentSnapshot().getStudyMedium().name() : null;
            case "Paper Writing Mode" -> submission.getStudentSnapshot() != null && submission.getStudentSnapshot().getPaperWritingMode() != null
                    ? submission.getStudentSnapshot().getPaperWritingMode().name() : null;
            case "Paper Center" -> {
                if (submission.getStudentSnapshot() != null && submission.getStudentSnapshot().getPaperCenterId() != null) {
                    String centerId = submission.getStudentSnapshot().getPaperCenterId();
                    yield paperCenterNameMap.getOrDefault(centerId, "");
                }
                yield null;
            }
            case "Portal", "Portal Name" -> submission.getPortalNameAtSubmission();
            case "Status" -> submission.getStatus() != null ? submission.getStatus().name() : null;
            case "Submitted At" -> submission.getSubmittedAt() != null ? submission.getSubmittedAt().format(DATE_FORMATTER) : null;
            case "Last Updated" -> submission.getLastUpdatedAt() != null ? submission.getLastUpdatedAt().format(DATE_FORMATTER) : null;
            case "Rejection Reason" -> submission.getRejectionReason();
            default -> {
                log.warn("Unknown column name: {}", columnName);
                yield null;
            }
        };
    }
}
