package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Entity representing a file uploaded as part of a payment submission.
 * Each file is associated with a specific submission and stores metadata about the file.
 */
@Getter
@Setter
@Entity
@Table(name = "uploaded_files")
public class UploadedFile extends BaseEntity {
    
    /**
     * External file ID reference (e.g., from a file storage service).
     */
    @Column(nullable = false)
    private UUID fileId;
    
    /**
     * Original filename of the uploaded file.
     */
    @Column(nullable = false)
    private String fileName;
    
    /**
     * MIME type or file extension of the uploaded file.
     */
    @Column(nullable = false)
    private String fileType;
    
    /**
     * Reference to the payment submission this file belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private PaymentSubmission submission;
    
}
