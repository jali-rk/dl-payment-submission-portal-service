package dopaminelite.payment_portal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayHere merchant settings, bound from {@code payhere.*} in application.yml.
 *
 * <p>The defaults are placeholders so the service runs before the client provides real
 * credentials. Swapping the {@code PAYHERE_*} environment variables is the only change needed
 * to go live; no code depends on the placeholder values.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "payhere")
public class PayHereProperties {

    /**
     * PayHere merchant ID.
     */
    private String merchantId = "DUMMY_MERCHANT_ID";

    /**
     * PayHere merchant secret. Used only to sign checkout requests and verify notifications;
     * never returned to clients.
     */
    private String merchantSecret = "DUMMY_SECRET";

    /**
     * Currency every online charge is made in.
     */
    private String currency = "LKR";

    /**
     * Checkout URL the browser posts the payment form to. Sandbox by default.
     */
    private String checkoutUrl = "https://sandbox.payhere.lk/pay/checkout";

}
