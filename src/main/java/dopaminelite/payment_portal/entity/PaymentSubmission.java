package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
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
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_id", nullable = false)
    private PaymentPortal portal;
    
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
        submittedAt = LocalDateTime.now();
        lastUpdatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = LocalDateTime.now();
    }
    
}
