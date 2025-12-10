package dopaminelite.payment_portal.entity;

import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "payment_submissions")
public class PaymentSubmission extends BaseEntity {
    
    @Column(nullable = false)
    private UUID studentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_id", nullable = false)
    private PaymentPortal portal;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.PENDING;
    
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;
    
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UploadedFile> uploadedFiles = new ArrayList<>();
    
    @Column(nullable = false)
    private String portalNameAtSubmission;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;
    
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
