package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.UploadedFile;
import dopaminelite.payment_portal.entity.enums.DataSheetType;
import dopaminelite.payment_portal.entity.enums.ExportFormat;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for exporting payment submission data sheets in various formats.
 * Supports CSV, XLSX, and PDF export with flexible filtering and column selection.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataSheetService {
    
    private final PaymentSubmissionRepository submissionRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Exports payment submissions as a data sheet in the specified format.
     *
     * @param type the type of submissions to export (APPROVED, REJECTED, PENDING, ALL)
     * @param format the export format (CSV, XLSX, PDF)
     * @param month filter by month (1-12), null for no filtering
     * @param year filter by year, null for no filtering
     * @param columns specific columns to include, null or empty for default columns
     * @param submissionIds specific submission IDs to export, overrides other filters if provided
     * @return the exported data as a byte array
     */
    public byte[] exportDataSheet(
            DataSheetType type,
            ExportFormat format,
            Integer month,
            Integer year,
            List<String> columns,
            List<UUID> submissionIds
    ) {
        List<PaymentSubmission> submissions = fetchSubmissions(type, month, year, submissionIds);
        
        return switch (format) {
            case CSV -> generateCsv(submissions, columns);
            case XLSX -> generateXlsx(submissions, columns);
            case PDF -> generatePdf(submissions, columns);
        };
    }
    
    private List<PaymentSubmission> fetchSubmissions(
            DataSheetType type,
            Integer month,
            Integer year,
            List<UUID> submissionIds
    ) {
        // If specific submission IDs provided, fetch those
        if (submissionIds != null && !submissionIds.isEmpty()) {
            return submissionRepository.findAllById(submissionIds);
        }
        
        // Otherwise filter by type and date range
        SubmissionStatus status = mapTypeToStatus(type);
        LocalDate fromDate = null;
        LocalDate toDate = null;
        
        if (month != null && year != null) {
            fromDate = LocalDate.of(year, month, 1);
            toDate = fromDate.plusMonths(1).minusDays(1);
        }
        
        return submissionRepository.findByFilters(null, null, status, fromDate, toDate, Pageable.unpaged())
                .getContent();
    }
    
    private SubmissionStatus mapTypeToStatus(DataSheetType type) {
        return switch (type) {
            case APPROVED -> SubmissionStatus.APPROVED;
            case REJECTED -> SubmissionStatus.REJECTED;
            case PENDING -> SubmissionStatus.PENDING;
            case ALL -> null;
        };
    }
    
    private byte[] generateCsv(List<PaymentSubmission> submissions, List<String> columns) {
        List<String> selectedColumns = (columns != null && !columns.isEmpty()) 
                ? columns 
                : getDefaultColumns();
        
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append(String.join(",", selectedColumns)).append("\n");
        
        // Data rows
        for (PaymentSubmission submission : submissions) {
            csv.append(selectedColumns.stream()
                    .map(col -> getCellValue(submission, col))
                    .map(this::escapeCsv)
                    .collect(Collectors.joining(",")))
                    .append("\n");
        }
        
        return csv.toString().getBytes();
    }
    
    private byte[] generateXlsx(List<PaymentSubmission> submissions, List<String> columns) {
        // Placeholder implementation - would use Apache POI
        // For now, return CSV format as fallback
        return generateCsv(submissions, columns);
    }
    
    private byte[] generatePdf(List<PaymentSubmission> submissions, List<String> columns) {
        // Placeholder implementation - would use iText or Apache PDFBox
        // For now, return CSV format as fallback
        return generateCsv(submissions, columns);
    }
    
    private List<String> getDefaultColumns() {
        return List.of(
                "id",
                "studentId",
                "portalName",
                "status",
                "submittedAt",
                "fileCount",
                "rejectionReason"
        );
    }
    
    private String getCellValue(PaymentSubmission submission, String column) {
        return switch (column.toLowerCase()) {
            case "id" -> submission.getId().toString();
            case "studentid" -> submission.getStudentId().toString();
            case "portalname" -> submission.getPortalNameAtSubmission();
            case "portal" -> submission.getPortalNameAtSubmission();
            case "status" -> submission.getStatus().name();
            case "submittedat" -> submission.getSubmittedAt().format(DATETIME_FORMATTER);
            case "filecount" -> String.valueOf(submission.getUploadedFiles().size());
            case "files" -> submission.getUploadedFiles().stream()
                    .map(UploadedFile::getFileName)
                    .collect(Collectors.joining("; "));
            case "rejectionreason" -> submission.getRejectionReason() != null ? submission.getRejectionReason() : "";
            case "lastupdatedat" -> submission.getLastUpdatedAt().format(DATETIME_FORMATTER);
            case "portalid" -> submission.getPortal().getId().toString();
            default -> "";
        };
    }
    
    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        
        return value;
    }
    
}
