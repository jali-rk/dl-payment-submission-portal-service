package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaymentPortal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentPortalRepository extends JpaRepository<PaymentPortal, UUID>, JpaSpecificationExecutor<PaymentPortal> {
    
    Optional<PaymentPortal> findByName(String name);
    
    boolean existsByName(String name);
    
    Page<PaymentPortal> findByMonthAndYear(Integer month, Integer year, Pageable pageable);
    
}
