package dopaminelite.payment_portal.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Base entity class providing a UUID-based primary key for all entities.
 * All domain entities should extend this class to inherit the common identifier.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity implements Serializable {
    
    /**
     * Unique identifier for the entity, automatically generated using UUID strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
}
