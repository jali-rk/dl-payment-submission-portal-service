package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.config.PayHereProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expected values were computed independently (Python hashlib) from PayHere's documented formula
 * with the placeholder credentials, so these pin the exact byte layout PayHere expects.
 */
class PayHereSignatureServiceTest {

    private final PayHereSignatureService service = new PayHereSignatureService(new PayHereProperties());

    @Test
    void checkoutHashMatchesPayHereFormula() {
        assertEquals("57FB993228CFB2DC587B71AB22E073FA",
                service.checkoutHash("DLORDER1", new BigDecimal("2500"), "LKR"));
    }

    @Test
    void amountIsFormattedWithTwoDecimalsAndNoGrouping() {
        assertEquals("2500.00", service.formatAmount(new BigDecimal("2500")));
        assertEquals("1234567.50", service.formatAmount(new BigDecimal("1234567.5")));
    }

    @Test
    void acceptsCorrectlySignedNotification() {
        assertTrue(service.isValidNotification("DUMMY_MERCHANT_ID", "DLORDER1", "2500.00", "LKR", "2",
                "78B7293A8D40A82B795EB091B01046DC"));
        // PayHere's casing isn't guaranteed; compare case-insensitively.
        assertTrue(service.isValidNotification("DUMMY_MERCHANT_ID", "DLORDER1", "2500.00", "LKR", "2",
                "78b7293a8d40a82b795eb091b01046dc"));
    }

    @Test
    void rejectsTamperedNotification() {
        String sig = "78B7293A8D40A82B795EB091B01046DC";
        assertFalse(service.isValidNotification("DUMMY_MERCHANT_ID", "DLORDER1", "1.00", "LKR", "2", sig));
        assertFalse(service.isValidNotification("DUMMY_MERCHANT_ID", "DLORDER1", "2500.00", "LKR", "-2", sig));
        assertFalse(service.isValidNotification("DUMMY_MERCHANT_ID", "DLORDER2", "2500.00", "LKR", "2", sig));
        assertFalse(service.isValidNotification("DUMMY_MERCHANT_ID", "DLORDER1", "2500.00", "LKR", "2", null));
    }
}
