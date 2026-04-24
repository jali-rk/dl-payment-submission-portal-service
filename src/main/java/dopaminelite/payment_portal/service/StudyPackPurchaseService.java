package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.entity.StudyPack;
import dopaminelite.payment_portal.entity.StudyPackPurchase;
import dopaminelite.payment_portal.entity.enums.PurchaseStatus;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.repository.StudyPackPurchaseRepository;
import dopaminelite.payment_portal.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing study pack purchases and enrollment.
 * Handles payment confirmation and automatic enrollment in all classes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyPackPurchaseService {
    
    private final StudyPackPurchaseRepository purchaseRepository;
    private final StudyPackRepository studyPackRepository;
    private final EnrollmentService enrollmentService;
    
    /**
     * Creates a new study pack purchase record.
     * This should be called when initiating payment.
     *
     * @param studentId the student UUID
     * @param studyPackId the study pack UUID
     * @param amount the purchase amount
     * @param currency the currency code
     * @param paymentGateway the payment gateway name
     * @param paymentSessionId the payment session ID from gateway
     * @return the created purchase record
     */
    @Transactional
    public StudyPackPurchase createPurchase(
            UUID studentId,
            UUID studyPackId,
            BigDecimal amount,
            String currency,
            String paymentGateway,
            String paymentSessionId
    ) {
        log.info("Creating study pack purchase - studentId: {}, studyPackId: {}, amount: {}, gateway: {}",
                studentId, studyPackId, amount, paymentGateway);
        
        // Verify study pack exists
        StudyPack studyPack = studyPackRepository.findById(studyPackId)
                .orElseThrow(() -> new ResourceNotFoundException("Study pack not found with id: " + studyPackId));
        
        StudyPackPurchase purchase = new StudyPackPurchase();
        purchase.setStudentId(studentId);
        purchase.setStudyPack(studyPack);
        purchase.setAmount(amount);
        purchase.setCurrency(currency);
        purchase.setPaymentGateway(paymentGateway);
        purchase.setPaymentSessionId(paymentSessionId);
        purchase.setPurchaseStatus(PurchaseStatus.PENDING);
        purchase.setEnrollmentCompleted(false);
        
        StudyPackPurchase savedPurchase = purchaseRepository.save(purchase);
        log.info("Study pack purchase created with id: {}", savedPurchase.getId());
        
        return savedPurchase;
    }
    
    /**
     * Handles successful payment confirmation from webhook.
     * Updates purchase status and enrolls student in all classes.
     * This method is transactional to ensure atomicity.
     *
     * @param paymentSessionId the payment session ID from gateway
     * @param transactionId the transaction ID from gateway
     * @throws ResourceNotFoundException if purchase not found
     */
    @Transactional
    public void handlePaymentSuccess(String paymentSessionId, String transactionId) {
        log.info("Handling payment success - sessionId: {}, transactionId: {}", paymentSessionId, transactionId);
        
        // Find purchase by session ID
        StudyPackPurchase purchase = purchaseRepository.findByPaymentSessionId(paymentSessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Purchase not found for payment session: " + paymentSessionId));
        
        // Check if already processed
        if (purchase.getPurchaseStatus() == PurchaseStatus.COMPLETED) {
            log.warn("Purchase {} already completed, skipping", purchase.getId());
            return;
        }
        
        try {
            // Update purchase status
            purchase.setPurchaseStatus(PurchaseStatus.COMPLETED);
            purchase.setTransactionId(transactionId);
            
            // Get study pack with class IDs
            StudyPack studyPack = purchase.getStudyPack();
            List<String> classIds = studyPack.getClassIds();
            
            log.info("Enrolling student {} in {} classes from study pack {}",
                    purchase.getStudentId(), classIds.size(), studyPack.getId());
            
            // Enroll student in all classes
            enrollStudentInClasses(purchase.getStudentId(), classIds);
            
            // Mark enrollment as completed
            purchase.setEnrollmentCompleted(true);
            purchase.setEnrollmentError(null);
            
            purchaseRepository.save(purchase);
            
            log.info("Successfully completed purchase {} and enrolled student in {} classes",
                    purchase.getId(), classIds.size());
            
        } catch (Exception e) {
            log.error("Failed to enroll student in classes for purchase {}", purchase.getId(), e);
            
            // Mark purchase as completed but enrollment failed
            purchase.setPurchaseStatus(PurchaseStatus.COMPLETED);
            purchase.setTransactionId(transactionId);
            purchase.setEnrollmentCompleted(false);
            purchase.setEnrollmentError(e.getMessage());
            
            purchaseRepository.save(purchase);
            
            // Re-throw to trigger transaction rollback if needed
            throw new RuntimeException("Enrollment failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Enrolls a student in multiple classes.
     * Uses the existing enrollment service to maintain consistency.
     *
     * @param studentId the student UUID
     * @param classIds list of class IDs (folder IDs)
     */
    private void enrollStudentInClasses(UUID studentId, List<String> classIds) {
        for (String classId : classIds) {
            try {
                log.debug("Enrolling student {} in class {}", studentId, classId);
                enrollmentService.enrollStudent(studentId, classId);
            } catch (Exception e) {
                log.error("Failed to enroll student {} in class {}", studentId, classId, e);
                throw new RuntimeException("Failed to enroll in class " + classId + ": " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Handles failed payment from webhook.
     *
     * @param paymentSessionId the payment session ID from gateway
     */
    @Transactional
    public void handlePaymentFailure(String paymentSessionId) {
        log.info("Handling payment failure - sessionId: {}", paymentSessionId);
        
        StudyPackPurchase purchase = purchaseRepository.findByPaymentSessionId(paymentSessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Purchase not found for payment session: " + paymentSessionId));
        
        purchase.setPurchaseStatus(PurchaseStatus.FAILED);
        purchaseRepository.save(purchase);
        
        log.info("Marked purchase {} as failed", purchase.getId());
    }
    
    /**
     * Retrieves all purchases for a student.
     *
     * @param studentId the student UUID
     * @return list of purchases
     */
    public List<StudyPackPurchase> getStudentPurchases(UUID studentId) {
        return purchaseRepository.findByStudentId(studentId);
    }
    
    /**
     * Retrieves completed purchases for a student.
     *
     * @param studentId the student UUID
     * @return list of completed purchases
     */
    public List<StudyPackPurchase> getCompletedPurchases(UUID studentId) {
        return purchaseRepository.findByStudentIdAndPurchaseStatus(studentId, PurchaseStatus.COMPLETED);
    }
    
}
