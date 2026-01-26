package dopaminelite.payment_portal.dto.submission;

import dopaminelite.payment_portal.entity.enums.PaperWritingMode;
import dopaminelite.payment_portal.entity.enums.StudyMedium;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a snapshot of student information at the time of payment submission.
 * This data is captured and persisted for audit, filtering, and reporting purposes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentSnapshotDto {

    /**
     * Student's unique code/identification number within the system.
     */
    @Size(max = 50, message = "Code number must not exceed 50 characters")
    private String codeNumber;

    /**
     * Full name of the student.
     */
    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    /**
     * Email address of the student.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    /**
     * Primary WhatsApp contact number of the student.
     */
    @NotBlank(message = "WhatsApp number is required")
    @Size(max = 20, message = "WhatsApp number must not exceed 20 characters")
    private String whatsappNumber;

    /**
     * Secondary/alternative phone number of the student.
     */
    @Size(max = 20, message = "Secondary phone number must not exceed 20 characters")
    private String secondaryPhoneNumber;

    /**
     * Physical address of the student.
     */
    @NotBlank(message = "Address is required")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    /**
     * National Identity Card (NIC) number of the student.
     */
    @Size(max = 20, message = "NIC must not exceed 20 characters")
    private String nic;

    /**
     * School name the student is attending or attended.
     */
    @Size(max = 255, message = "School must not exceed 255 characters")
    private String school;

    /**
     * Mode in which the student writes exam papers (PHYSICAL or ONLINE).
     */
    private PaperWritingMode paperWritingMode;

    /**
     * ID of the paper center where the student writes exams (if paper writing mode is PHYSICAL).
     */
    @Size(max = 100, message = "Paper center ID must not exceed 100 characters")
    private String paperCenterId;

    /**
     * Language medium of study for the student (SINHALA, TAMIL, or ENGLISH).
     */
    private StudyMedium studyMedium;
}
