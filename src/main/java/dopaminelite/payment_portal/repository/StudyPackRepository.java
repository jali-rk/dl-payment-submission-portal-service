package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.StudyPack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository interface for StudyPack entity operations.
 * Provides CRUD operations and custom queries for study pack management.
 */
@Repository
public interface StudyPackRepository extends JpaRepository<StudyPack, UUID> {
    
    /**
     * Finds all study packs filtered by active status with pagination.
     *
     * @param isActive the active status to filter by
     * @param pageable pagination information
     * @return a page of study packs matching the criteria
     */
    Page<StudyPack> findByIsActive(Boolean isActive, Pageable pageable);
    
}
