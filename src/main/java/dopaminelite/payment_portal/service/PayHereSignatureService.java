package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.config.PayHereProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * PayHere's MD5 signing scheme, for both directions:
 *
 * <ul>
 *   <li>Checkout hash: {@code UPPER(MD5(merchant_id + order_id + amount + currency + UPPER(MD5(secret))))}</li>
 *   <li>Notification signature: {@code UPPER(MD5(merchant_id + order_id + payhere_amount + payhere_currency
 *       + status_code + UPPER(MD5(secret))))}</li>
 * </ul>
 *
 * <p>The algorithm is real; only the credentials are placeholders until the client provides
 * them. Verification is never skipped. With the placeholder secret, a test notification just has
 * to be signed with "DUMMY_SECRET", so there is no bypass flag that could let forged payments
 * through if it leaked into production.
 */
@Component
@RequiredArgsConstructor
public class PayHereSignatureService {

    private final PayHereProperties properties;

    /**
     * Formats an amount the way PayHere hashes it: two decimals, no grouping separators.
     */
    public String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Hash the browser sends with the checkout form.
     */
    public String checkoutHash(String orderId, BigDecimal amount, String currency) {
        return md5Upper(properties.getMerchantId() + orderId + formatAmount(amount) + currency + hashedSecret());
    }

    /**
     * Checks a notification's {@code md5sig} against the fields PayHere signed. The amount is
     * hashed exactly as PayHere sent it, not reformatted.
     */
    public boolean isValidNotification(String merchantId, String orderId, String payhereAmount,
                                       String payhereCurrency, String statusCode, String md5sig) {
        if (merchantId == null || orderId == null || payhereAmount == null
                || payhereCurrency == null || statusCode == null || md5sig == null) {
            return false;
        }
        String expected = md5Upper(merchantId + orderId + payhereAmount + payhereCurrency + statusCode + hashedSecret());
        // Constant-time comparison so the signature can't be discovered byte by byte through timing.
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            md5sig.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String hashedSecret() {
        return md5Upper(properties.getMerchantSecret());
    }

    private static String md5Upper(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every compliant JVM ships MD5.
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
