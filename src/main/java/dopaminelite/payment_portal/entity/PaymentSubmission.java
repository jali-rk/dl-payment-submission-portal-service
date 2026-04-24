package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a student's payment submission to a specific portal.
 * Each submission contains uploaded files and tracks the approval status.
 * Submissions can be PENDING, APPROVED, or REJECTED by administrators.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_submissions")
public class PaymentSubmission extends BaseEntity {
    
    /**
     * UUID of the student who made this submission.
     */
    @Column(nullable = false)
    private UUID studentId;
    
    /**
     * Reference to the payment portal this submission belongs to.
     * For study pack submissions, this may be null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_id")
    private PaymentPortal portal;
    
    /**
     * Type of submission: PORTAL (regular class) or STUDY_PACK.
     * Defaults to PORTAL for backward compatibility.
     */
    @Column(name = "submission_type", nullable = false, length = 20)
    private String submissionType = "PORTAL";
    
    /**
     * Reference to the study pack if this is a study pack submission.
     * Null for regular portal submissions.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_pack_id")
    private StudyPack studyPack;
    
    /**
     * Current status of the submission (PENDING, APPROVED, or REJECTED).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.PENDING;
    
    /**
     * Reason provided by admin when rejecting a submission. Required when status is REJECTED.
     */
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
    
    /**
     * List of files uploaded with this submission. Managed with cascade ALL.
     */
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UploadedFile> uploadedFiles = new ArrayList<>();
    
    /**
     * Snapshot of the portal name at the time of submission for audit purposes.
     */
    @Column(nullable = false)
    private String portalNameAtSubmission;

    /**
     * Embedded snapshot of student information at the time of submission.
     * Used for filtering, reporting, and audit purposes.
     */
    @Embedded
    private StudentSnapshot studentSnapshot;
    
    /**
     * Timestamp when the submission was first created. Set automatically on persist.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;
    
    /**
     * Timestamp of the last update to this submission. Updated automatically.
     */
    @Column(nullable = false)
    private LocalDateTime lastUpdatedAt;
    
    @PrePersist
    protected void onCreate() {
        ZoneId sriLankaZone = ZoneId.of("Asia/Colombo");
        submittedAt = LocalDateTime.now(sriLankaZone);
        lastUpdatedAt = LocalDateTime.now(sriLankaZone);
    }

    @PreUpdate
    protected void onUpdate() {
        ZoneId sriLankaZone = ZoneId.of("Asia/Colombo");
        lastUpdatedAt = LocalDateTime.now(sriLankaZone);
    }
    
}
