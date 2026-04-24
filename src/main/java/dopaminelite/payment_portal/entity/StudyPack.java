package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a study pack - a bundle of classes that students can purchase together.
 * Study packs allow students to buy multiple classes at a discounted price.
 */
@Getter
@Setter
@Entity
@Table(name = "study_packs")
public class StudyPack extends AuditableEntity {
    
    /**
     * Name of the study pack.
     */
    @Column(nullable = false)
    private String name;
    
    /**
     * Detailed description of what the study pack includes.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    
    /**
     * Price of the study pack.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    /**
     * List of class IDs (UUIDs as strings) included in this study pack.
     * Stored as a JSON array in the database.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "study_pack_class_ids", joinColumns = @JoinColumn(name = "study_pack_id"))
    @Column(name = "class_id")
    private List<String> classIds = new ArrayList<>();
    
    /**
     * Flag indicating whether this study pack is currently active and available for purchase.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    /**
     * URL to the thumbnail image for this study pack.
     */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;
    
}
