package dopaminelite.payment_portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base auditable entity class providing automatic audit tracking fields.
 * Extends BaseEntity and adds timestamps and user tracking for creation and modification.
 * Entities extending this class will automatically track who created/modified them and when.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity {
    
    /**
     * Timestamp when the entity was created. Automatically set on persist.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Timestamp when the entity was last modified. Automatically updated on changes.
     */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Identifier of the user who created the entity. Automatically set on persist.
     */
    @CreatedBy
    @Column(updatable = false)
    private String createdBy;
    
    /**
     * Identifier of the user who last modified the entity. Automatically updated on changes.
     */
    @LastModifiedBy
    private String updatedBy;
    
}
