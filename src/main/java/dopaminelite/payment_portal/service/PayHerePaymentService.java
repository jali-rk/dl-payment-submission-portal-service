package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.config.PayHereProperties;
import dopaminelite.payment_portal.dto.payhere.PayHereInitiateRequest;
import dopaminelite.payment_portal.dto.payhere.PayHereInitiateResponse;
import dopaminelite.payment_portal.dto.payhere.PayHereNotificationRequest;
import dopaminelite.payment_portal.dto.payhere.PaymentTransactionStatusResponse;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.PaymentSubmission;
import dopaminelite.payment_portal.entity.PaymentTransaction;
import dopaminelite.payment_portal.entity.StudentSnapshot;
import dopaminelite.payment_portal.entity.enums.PaymentGateway;
import dopaminelite.payment_portal.entity.enums.PaymentMethod;
import dopaminelite.payment_portal.entity.enums.PaymentTransactionStatus;
import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import dopaminelite.payment_portal.entity.enums.SubmissionStatus;
import dopaminelite.payment_portal.event.PaymentSubmissionApprovedEvent;
import dopaminelite.payment_portal.exception.DuplicateResourceException;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.mapper.PaymentSubmissionMapper;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import dopaminelite.payment_portal.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Online payments through PayHere: starting a checkout, applying PayHere's signed result, and
 * reporting a checkout's status back to the student.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayHerePaymentService {

    private static final String STATUS_SUCCESS = "2";
    private static final String STATUS_PENDING = "0";
    private static final String STATUS_CHARGEBACK = "-3";
    private static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");

    private final PaymentPortalRepository portalRepository;
    private final PaymentSubmissionRepository submissionRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentSubmissionMapper submissionMapper;
    private final PayHereSignatureService signatureService;
    private final PayHereProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Starts a checkout: validates the portal accepts online payments, records a PENDING
     * transaction at the portal's fee, and returns the signed checkout fields.
     */
    @Transactional
    public PayHereInitiateResponse initiate(PayHereInitiateRequest request) {
        PaymentPortal portal = portalRepository.findById(request.getPortalId())
                .orElseThrow(() -> ResourceNotFoundException.portalNotFound(request.getPortalId()));

        if (portal.getVisibility() != PortalVisibility.PUBLISHED) {
            throw ResourceNotFoundException.portalNotFound(request.getPortalId());
        }
        if (!Boolean.TRUE.equals(portal.getIsPaymentAllowed())) {
            throw ValidationException.paymentsNotAllowed(portal.getDisplayName());
        }
        if (!Boolean.TRUE.equals(portal.getAllowOnline())) {
            throw ValidationException.paymentMethodNotAllowed(portal.getDisplayName(), "Online");
        }
        BigDecimal fee = portal.getOnlineFee();
        if (fee == null || fee.signum() <= 0) {
            // Portal service refuses to save this state, so it means bad data, not a user error.
            log.error("Portal {} allows online payments but has no valid fee: {}", portal.getId(), fee);
            throw new ValidationException("Online payment is not set up for this portal yet");
        }

        if (!activeSubmissions(request.getStudentId(), portal.getId()).isEmpty()) {
            throw new DuplicateResourceException(
                "You already have a pending or approved payment for this portal");
        }

        PaymentTransaction tx = new PaymentTransaction();
        tx.setOrderId(newOrderId());
        tx.setPortal(portal);
        tx.setStudentId(request.getStudentId());
        tx.setAmount(fee);
        tx.setCurrency(properties.getCurrency());
        tx.setStatus(PaymentTransactionStatus.PENDING);
        tx.setGateway(PaymentGateway.PAYHERE);
        tx.setStudentSnapshot(submissionMapper.toStudentSnapshotEntity(request.getStudentSnapshot()));
        transactionRepository.save(tx);

        log.info("PayHere checkout started: orderId={}, portalId={}, studentId={}, amount={} {}",
                tx.getOrderId(), portal.getId(), tx.getStudentId(), fee, tx.getCurrency());

        return new PayHereInitiateResponse(
                tx.getOrderId(),
                signatureService.checkoutHash(tx.getOrderId(), fee, tx.getCurrency()),
                signatureService.formatAmount(fee),
                tx.getCurrency(),
                properties.getMerchantId(),
                properties.getCheckoutUrl(),
                portal.getId(),
                portal.getDisplayName()
        );
    }

    /**
     * Applies PayHere's server-to-server notification. Safe to receive more than once: the
     * transaction row is locked, and a COMPLETED transaction is never re-processed.
     *
     * @throws ValidationException if the signature or merchant ID is wrong
     * @throws ResourceNotFoundException if the order ID is unknown
     */
    @Transactional
    public PaymentTransactionStatusResponse applyResult(PayHereNotificationRequest n) {
        boolean signatureValid = signatureService.isValidNotification(
                n.getMerchantId(), n.getOrderId(), n.getPayhereAmount(),
                n.getPayhereCurrency(), n.getStatusCode(), n.getMd5sig());
        if (!signatureValid || !properties.getMerchantId().equals(n.getMerchantId())) {
            log.warn("Rejected PayHere notification with invalid signature or merchant: orderId={}, merchantId={}",
                    n.getOrderId(), n.getMerchantId());
            throw new ValidationException("Invalid payment notification signature");
        }

        PaymentTransaction tx = transactionRepository.findByOrderIdForUpdate(n.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found: " + n.getOrderId()));

        log.info("PayHere notification: orderId={}, statusCode={}, paymentId={}, currentStatus={}",
                tx.getOrderId(), n.getStatusCode(), n.getPaymentId(), tx.getStatus());

        if (tx.getStatus() == PaymentTransactionStatus.COMPLETED) {
            if (STATUS_CHARGEBACK.equals(n.getStatusCode())) {
                // Revoking an approved enrollment is a business decision, so it's flagged for an
                // admin instead of being done automatically.
                log.error("CHARGEBACK on completed PayHere payment: orderId={}, submissionId={}. Needs admin review.",
                        tx.getOrderId(), tx.getSubmission() != null ? tx.getSubmission().getId() : null);
                recordGatewayFields(tx, n);
                transactionRepository.save(tx);
            }
            return toStatusResponse(tx);
        }

        recordGatewayFields(tx, n);

        if (STATUS_SUCCESS.equals(n.getStatusCode())) {
            if (!amountMatches(tx, n)) {
                log.error("PayHere amount mismatch: orderId={}, expected={} {}, received={} {}",
                        tx.getOrderId(), tx.getAmount(), tx.getCurrency(), n.getPayhereAmount(), n.getPayhereCurrency());
                tx.setStatus(PaymentTransactionStatus.FAILED);
                tx.setGatewayStatusMessage("Amount mismatch: paid " + n.getPayhereAmount() + " " + n.getPayhereCurrency());
                transactionRepository.save(tx);
                return toStatusResponse(tx);
            }
            completeTransaction(tx);
        } else if (!STATUS_PENDING.equals(n.getStatusCode())) {
            // -1 cancelled, -2 failed, -3 charged back (before we'd completed it)
            tx.setStatus(PaymentTransactionStatus.FAILED);
        }

        transactionRepository.save(tx);
        return toStatusResponse(tx);
    }

    /**
     * Status of one of the student's own checkouts.
     *
     * @throws ResourceNotFoundException if the order doesn't exist or belongs to someone else
     */
    @Transactional(readOnly = true)
    public PaymentTransactionStatusResponse getStatus(String orderId, UUID studentId) {
        PaymentTransaction tx = transactionRepository.findByOrderIdAndStudentId(orderId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found: " + orderId));
        return toStatusResponse(tx);
    }

    private void completeTransaction(PaymentTransaction tx) {
        tx.setStatus(PaymentTransactionStatus.COMPLETED);
        tx.setCompletedAt(LocalDateTime.now(SRI_LANKA));

        PaymentPortal portal = tx.getPortal();
        List<PaymentSubmission> active = activeSubmissions(tx.getStudentId(), portal.getId());
        PaymentSubmission approved = active.stream()
                .filter(s -> s.getStatus() == SubmissionStatus.APPROVED)
                .findFirst()
                .orElse(null);

        if (approved != null) {
            // Student was already approved (e.g. a second tab, or a slip approved meanwhile).
            // The money is real, so keep the record, link it, and flag the likely refund.
            log.warn("PayHere payment {} completed but student {} already has APPROVED submission {} for portal {}. "
                    + "Possible double payment; refund may be needed.",
                    tx.getOrderId(), tx.getStudentId(), approved.getId(), portal.getId());
            tx.setSubmission(approved);
            return;
        }
        if (!active.isEmpty()) {
            log.warn("PayHere payment {} completed while student {} has a PENDING slip submission for portal {}. "
                    + "Creating the approved online submission; the slip still needs an admin decision.",
                    tx.getOrderId(), tx.getStudentId(), portal.getId());
        }

        PaymentSubmission submission = new PaymentSubmission();
        submission.setStudentId(tx.getStudentId());
        submission.setPortal(portal);
        submission.setStatus(SubmissionStatus.APPROVED);
        submission.setPaymentMethod(PaymentMethod.ONLINE_PAYHERE);
        submission.setPortalNameAtSubmission(portal.getDisplayName());
        submission.setStudentSnapshot(tx.getStudentSnapshot() != null ? copyOf(tx.getStudentSnapshot()) : null);
        PaymentSubmission saved = submissionRepository.save(submission);
        tx.setSubmission(saved);

        // Same event an admin approval publishes, so paper slots are created after commit.
        eventPublisher.publishEvent(new PaymentSubmissionApprovedEvent(saved.getId()));
        log.info("PayHere payment {} completed; created APPROVED submission {}", tx.getOrderId(), saved.getId());
    }

    private List<PaymentSubmission> activeSubmissions(UUID studentId, UUID portalId) {
        return submissionRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("studentId"), studentId),
                cb.equal(root.get("portal").get("id"), portalId),
                root.get("status").in(SubmissionStatus.PENDING, SubmissionStatus.APPROVED)
        ));
    }

    private boolean amountMatches(PaymentTransaction tx, PayHereNotificationRequest n) {
        try {
            return new BigDecimal(n.getPayhereAmount()).compareTo(tx.getAmount()) == 0
                    && tx.getCurrency().equals(n.getPayhereCurrency());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void recordGatewayFields(PaymentTransaction tx, PayHereNotificationRequest n) {
        tx.setGatewayPaymentId(n.getPaymentId());
        tx.setGatewayStatusMessage(n.getStatusMessage());
        tx.setGatewayMethod(n.getMethod());
        try {
            tx.setGatewayStatusCode(Integer.valueOf(n.getStatusCode()));
        } catch (NumberFormatException e) {
            tx.setGatewayStatusCode(null);
        }
    }

    /**
     * Embeddables can't be shared between two entities, so the submission gets its own copy.
     */
    private static StudentSnapshot copyOf(StudentSnapshot src) {
        StudentSnapshot dst = new StudentSnapshot();
        dst.setCodeNumber(src.getCodeNumber());
        dst.setFullName(src.getFullName());
        dst.setEmail(src.getEmail());
        dst.setWhatsappNumber(src.getWhatsappNumber());
        dst.setSecondaryPhoneNumber(src.getSecondaryPhoneNumber());
        dst.setAddress(src.getAddress());
        dst.setNic(src.getNic());
        dst.setSchool(src.getSchool());
        dst.setPaperWritingMode(src.getPaperWritingMode());
        dst.setPaperCenterId(src.getPaperCenterId());
        dst.setStudyMedium(src.getStudyMedium());
        return dst;
    }

    private static String newOrderId() {
        // "DL" + 20 hex chars: short enough for PayHere, unique enough with the DB constraint behind it.
        return "DL" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private PaymentTransactionStatusResponse toStatusResponse(PaymentTransaction tx) {
        return new PaymentTransactionStatusResponse(
                tx.getOrderId(),
                tx.getStatus(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getPortal().getId(),
                tx.getPortal().getDisplayName(),
                tx.getSubmission() != null ? tx.getSubmission().getId() : null,
                tx.getGatewayStatusMessage(),
                tx.getCreatedAt(),
                tx.getCompletedAt()
        );
    }
}
