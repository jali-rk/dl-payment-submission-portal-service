package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.service.StudyPackPurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for handling payment gateway webhooks.
 * Receives payment confirmation events and processes study pack purchases.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {
    
    private final StudyPackPurchaseService purchaseService;
    
    /**
     * Handles payment success webhook from payment gateway.
     * This endpoint should be called by your payment gateway (Stripe, Razorpay, etc.)
     * when a payment is successfully completed.
     *
     * Example payload structure (adjust based on your gateway):
     * {
     *   "event": "payment.success",
     *   "sessionId": "session_123",
     *   "transactionId": "txn_456",
     *   "type": "STUDY_PACK"
     * }
     *
     * @param payload the webhook payload from payment gateway
     * @return HTTP 200 OK if processed successfully
     */
    @PostMapping("/success")
    public ResponseEntity<Map<String, String>> handlePaymentSuccess(@RequestBody Map<String, Object> payload) {
        log.info("Received payment success webhook: {}", payload);
        
        try {
            // Extract payment details from payload
            // NOTE: Adjust these field names based on your payment gateway's webhook format
            String sessionId = (String) payload.get("sessionId");
            String transactionId = (String) payload.get("transactionId");
            String type = (String) payload.get("type");
            
            // Validate required fields
            if (sessionId == null || transactionId == null) {
                log.error("Missing required fields in webhook payload");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing sessionId or transactionId"));
            }
            
            // Check if this is a study pack purchase
            if ("STUDY_PACK".equals(type)) {
                log.info("Processing study pack purchase - sessionId: {}", sessionId);
                purchaseService.handlePaymentSuccess(sessionId, transactionId);
                log.info("Successfully processed study pack purchase");
            } else {
                log.info("Payment is not for study pack, type: {}", type);
            }
            
            return ResponseEntity.ok(Map.of("status", "success", "message", "Webhook processed"));
            
        } catch (Exception e) {
            log.error("Error processing payment success webhook", e);
            // Return 200 to prevent webhook retries, but log the error
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }
    
    /**
     * Handles payment failure webhook from payment gateway.
     *
     * @param payload the webhook payload from payment gateway
     * @return HTTP 200 OK if processed successfully
     */
    @PostMapping("/failure")
    public ResponseEntity<Map<String, String>> handlePaymentFailure(@RequestBody Map<String, Object> payload) {
        log.info("Received payment failure webhook: {}", payload);
        
        try {
            String sessionId = (String) payload.get("sessionId");
            String type = (String) payload.get("type");
            
            if (sessionId == null) {
                log.error("Missing sessionId in webhook payload");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing sessionId"));
            }
            
            if ("STUDY_PACK".equals(type)) {
                log.info("Processing failed study pack purchase - sessionId: {}", sessionId);
                purchaseService.handlePaymentFailure(sessionId);
                log.info("Successfully processed failed purchase");
            }
            
            return ResponseEntity.ok(Map.of("status", "success", "message", "Webhook processed"));
            
        } catch (Exception e) {
            log.error("Error processing payment failure webhook", e);
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }
    
    /**
     * Generic webhook endpoint for payment gateways that send all events to one URL.
     * This can be used with Stripe, for example.
     *
     * @param payload the webhook payload
     * @return HTTP 200 OK
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> handleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received generic payment webhook: {}", payload);
        
        try {
            // Determine event type
            String eventType = (String) payload.get("event");
            
            if (eventType == null) {
                log.warn("No event type in webhook payload");
                return ResponseEntity.ok(Map.of("status", "ignored", "message", "No event type"));
            }
            
            // Route to appropriate handler based on event type
            switch (eventType) {
                case "payment.success":
                case "checkout.session.completed":
                    return handlePaymentSuccess(payload);
                    
                case "payment.failed":
                case "checkout.session.expired":
                    return handlePaymentFailure(payload);
                    
                default:
                    log.info("Unhandled event type: {}", eventType);
                    return ResponseEntity.ok(Map.of("status", "ignored", "message", "Event type not handled"));
            }
            
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }
    
}
