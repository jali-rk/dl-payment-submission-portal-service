package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.payhere.PayHereInitiateRequest;
import dopaminelite.payment_portal.dto.payhere.PayHereInitiateResponse;
import dopaminelite.payment_portal.dto.payhere.PayHereNotificationRequest;
import dopaminelite.payment_portal.dto.payhere.PaymentTransactionStatusResponse;
import dopaminelite.payment_portal.service.PayHerePaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Online payment endpoints. Like the rest of this service, these are called only by BFF, which
 * authenticates the student and supplies their ID.
 */
@RestController
@RequiredArgsConstructor
public class PayHerePaymentController {

    private final PayHerePaymentService payHerePaymentService;

    /**
     * Starts a PayHere checkout and returns the signed fields for the payment form.
     */
    @PostMapping("/payment/initiate-payhere")
    public ResponseEntity<PayHereInitiateResponse> initiate(@Valid @RequestBody PayHereInitiateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payHerePaymentService.initiate(request));
    }

    /**
     * Status of one of the student's checkouts. Returns 404 for another student's order.
     */
    @GetMapping("/payment/status/{orderId}")
    public ResponseEntity<PaymentTransactionStatusResponse> getStatus(
            @PathVariable String orderId,
            @RequestParam UUID studentId
    ) {
        return ResponseEntity.ok(payHerePaymentService.getStatus(orderId, studentId));
    }

    /**
     * Applies PayHere's server-to-server notification, forwarded by BFF. The md5sig is verified
     * here, where the merchant secret lives.
     */
    @PostMapping("/internal/payment-transactions/apply-result")
    public ResponseEntity<PaymentTransactionStatusResponse> applyResult(@RequestBody PayHereNotificationRequest request) {
        return ResponseEntity.ok(payHerePaymentService.applyResult(request));
    }
}
