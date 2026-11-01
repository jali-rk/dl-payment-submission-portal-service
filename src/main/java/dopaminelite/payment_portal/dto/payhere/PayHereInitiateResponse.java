package dopaminelite.payment_portal.dto.payhere;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Everything the checkout form needs from this service. BFF adds the return, cancel and notify
 * URLs and the customer fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayHereInitiateResponse {

    private String orderId;

    /**
     * Checkout hash signed with the merchant secret. The secret itself is never returned.
     */
    private String hash;

    /**
     * Amount formatted exactly as it was hashed (e.g. "2500.00"). Must reach PayHere unchanged.
     */
    private String fee;

    private String currency;

    private String merchantId;

    private String checkoutUrl;

    private UUID portalId;

    private String portalName;
}
