package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.PaperWritingMode;
import dopaminelite.payment_portal.entity.enums.StudyMedium;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Embeddable class representing a snapshot of student information at the time of payment submission.
 * This data is captured and persisted with each submission for audit and filtering purposes.
 * All fields are nullable to maintain backward compatibility with existing submissions.
 */
@Getter
@Setter
@Embeddable
public class StudentSnapshot {

    /**
     * Student's unique code/identification number within the system.
     */
    @Column(name = "student_code_number", length = 50)
    private String codeNumber;

    /**
     * Full name of the student at the time of submission.
     */
    @Column(name = "student_full_name", length = 255, nullable = false)
    private String fullName;

    /**
     * Email address of the student.
     */
    @Column(name = "student_email", length = 255, nullable = false)
    private String email;

    /**
     * Primary WhatsApp contact number of the student.
     */
    @Column(name = "student_whatsapp_number", length = 20, nullable = false)
    private String whatsappNumber;

    /**
     * Secondary/alternative phone number of the student.
     */
    @Column(name = "student_secondary_phone_number", length = 20)
    private String secondaryPhoneNumber;

    /**
     * Physical address of the student.
     */
    @Column(name = "student_address", length = 500, nullable = false)
    private String address;

    /**
     * National Identity Card (NIC) number of the student.
     */
    @Column(name = "student_nic", length = 20)
    private String nic;

    /**
     * School name the student is attending or attended.
     */
    @Column(name = "student_school", length = 255)
    private String school;

    /**
     * Mode in which the student writes exam papers (PHYSICAL or ONLINE).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "student_paper_writing_mode", length = 20)
    private PaperWritingMode paperWritingMode;

    /**
     * ID of the paper center where the student writes exams (if paper writing mode is PHYSICAL).
     */
    @Column(name = "student_paper_center_id", length = 100)
    private String paperCenterId;

    /**
     * Language medium of study for the student (SINHALA, TAMIL, or ENGLISH).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "student_study_medium", length = 20)
    private StudyMedium studyMedium;
}
