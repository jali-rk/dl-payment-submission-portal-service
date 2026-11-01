package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for online payment transactions.
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    /**
     * Scoped by student so one student can't look up another student's order.
     */
    Optional<PaymentTransaction> findByOrderIdAndStudentId(String orderId, UUID studentId);

    /**
     * Loads a transaction with a row lock so two deliveries of the same gateway notification
     * can't both create a submission.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.orderId = :orderId")
    Optional<PaymentTransaction> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
