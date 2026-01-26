package dopaminelite.payment_portal.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for exporting payment submissions.
 * Specifies which columns should be included in the export.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionExportRequest {

    /**
     * List of column names to include in the export.
     * Must contain at least one column.
     * Valid column names include: "Student Name", "Student ID", "Email", "WhatsApp Number",
     * "Secondary Phone", "Address", "NIC", "School", "Study Medium", "Paper Writing Mode",
     * "Paper Center", "Portal Name", "Status", "Submitted At", "Rejection Reason"
     */
    @NotNull(message = "Columns list is required")
    @NotEmpty(message = "At least one column is required")
    private List<String> columns;
}
