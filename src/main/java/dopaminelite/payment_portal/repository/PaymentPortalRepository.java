package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaymentPortal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PaymentPortal entity operations.
 * Provides CRUD operations and custom queries for payment portal management.
 */
@Repository
public interface PaymentPortalRepository extends JpaRepository<PaymentPortal, UUID>, JpaSpecificationExecutor<PaymentPortal> {
    
    /**
     * Finds a payment portal by its unique name.
     *
     * @param name the portal name
     * @return an Optional containing the portal if found, empty otherwise
     */
    Optional<PaymentPortal> findByName(String name);
    
    /**
     * Checks if a portal exists with the given name.
     *
     * @param name the portal name to check
     * @return true if a portal with this name exists, false otherwise
     */
    boolean existsByName(String name);
    
    /**
     * Finds all portals for a specific month and year with pagination.
     *
     * @param month the month (1-12)
     * @param year the year
     * @param pageable pagination information
     * @return a page of portals matching the criteria
     */
    Page<PaymentPortal> findByMonthAndYear(Integer month, Integer year, Pageable pageable);
    
}
