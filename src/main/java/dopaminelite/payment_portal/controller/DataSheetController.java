package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.entity.enums.DataSheetType;
import dopaminelite.payment_portal.entity.enums.ExportFormat;
import dopaminelite.payment_portal.service.DataSheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for exporting payment submission data sheets.
 * Provides endpoint for exporting submissions in various formats (CSV, XLSX, PDF).
 */
@RestController
@RequestMapping("/data-sheets")
@RequiredArgsConstructor
public class DataSheetController {
    
    private final DataSheetService dataSheetService;
    
    /**
     * Exports payment submissions as a downloadable data sheet.
     *
     * @param type the type of submissions to export (APPROVED, REJECTED, PENDING, ALL)
     * @param format the export format (CSV, XLSX, PDF)
     * @param month filter by month (1-12), optional
     * @param year filter by year, optional
     * @param columns specific columns to include in export, optional (defaults to all columns)
     * @param submissionIds specific submission IDs to export, optional (overrides other filters)
     * @return downloadable file as byte array with appropriate content type and filename
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDataSheet(
            @RequestParam DataSheetType type,
            @RequestParam ExportFormat format,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) List<String> columns,
            @RequestParam(required = false) List<UUID> submissionIds
    ) {
        byte[] data = dataSheetService.exportDataSheet(type, format, month, year, columns, submissionIds);
        
        String filename = generateFilename(type, format, month, year);
        String contentType = getContentType(format);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(data.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }
    
    private String generateFilename(DataSheetType type, ExportFormat format, Integer month, Integer year) {
        StringBuilder filename = new StringBuilder("payment-submissions-");
        filename.append(type.name().toLowerCase());
        
        if (month != null && year != null) {
            filename.append("-").append(year).append("-").append(String.format("%02d", month));
        }
        
        filename.append(".").append(format.name().toLowerCase());
        return filename.toString();
    }
    
    private String getContentType(ExportFormat format) {
        return switch (format) {
            case CSV -> "text/csv";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case PDF -> "application/pdf";
        };
    }
    
}
