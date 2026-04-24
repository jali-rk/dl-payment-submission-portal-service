package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.StudyPackPurchase;
import dopaminelite.payment_portal.entity.enums.PurchaseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for StudyPackPurchase entity operations.
 */
@Repository
public interface StudyPackPurchaseRepository extends JpaRepository<StudyPackPurchase, UUID> {
    
    /**
     * Finds a purchase by payment session ID.
     *
     * @param paymentSessionId the payment session ID from gateway
     * @return an Optional containing the purchase if found
     */
    @EntityGraph(attributePaths = {"studyPack", "studyPack.classIds"})
    Optional<StudyPackPurchase> findByPaymentSessionId(String paymentSessionId);
    
    /**
     * Finds all purchases by student ID.
     *
     * @param studentId the student UUID
     * @return list of purchases
     */
    @EntityGraph(attributePaths = {"studyPack", "studyPack.classIds"})
    List<StudyPackPurchase> findByStudentId(UUID studentId);
    
    /**
     * Finds all purchases by student ID and status.
     *
     * @param studentId the student UUID
     * @param status the purchase status
     * @return list of purchases
     */
    @EntityGraph(attributePaths = {"studyPack", "studyPack.classIds"})
    List<StudyPackPurchase> findByStudentIdAndPurchaseStatus(UUID studentId, PurchaseStatus status);
    
    /**
     * Finds a purchase by student ID and study pack ID.
     *
     * @param studentId the student UUID
     * @param studyPackId the study pack UUID
     * @return an Optional containing the purchase if found
     */
    @EntityGraph(attributePaths = {"studyPack", "studyPack.classIds"})
    Optional<StudyPackPurchase> findByStudentIdAndStudyPackId(UUID studentId, UUID studyPackId);
    
}
