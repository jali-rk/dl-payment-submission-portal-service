package dopaminelite.payment_portal.dto.payhere;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PayHere's server-to-server notification, forwarded by BFF as JSON with PayHere's original
 * snake_case field names. Fields stay raw strings because the signature is computed over them
 * exactly as PayHere sent them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayHereNotificationRequest {

    @JsonProperty("merchant_id")
    private String merchantId;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("payment_id")
    private String paymentId;

    @JsonProperty("payhere_amount")
    private String payhereAmount;

    @JsonProperty("payhere_currency")
    private String payhereCurrency;

    /**
     * 2 success, 0 pending, -1 cancelled, -2 failed, -3 charged back.
     */
    @JsonProperty("status_code")
    private String statusCode;

    @JsonProperty("md5sig")
    private String md5sig;

    @JsonProperty("status_message")
    private String statusMessage;

    /**
     * Payment instrument, e.g. VISA, MASTER.
     */
    @JsonProperty("method")
    private String method;
}
