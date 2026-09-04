package dopaminelite.payment_portal.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Extracts the acting user's ID from a bearer JWT's payload claims, without verifying the
 * token's signature — this project has no Spring Security / signature verification anywhere,
 * so this only ever identifies "who the caller claims to be," consistent with how
 * {@code PaymentPortalController} already resolves admin IDs today.
 */
@Component
public class JwtUserIdExtractor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extracts a user ID from the {@code id}/{@code sub}/{@code user_id}/{@code uid} claim of a
     * {@code Bearer} JWT. {@code id} is checked first — it's what the BFF's own
     * {@code generateAccessToken} actually puts in the payload (see {@code auth.controller.ts});
     * the others are kept as a fallback for any other token issuer.
     *
     * @param authorizationHeader the raw {@code Authorization} header value, may be null
     * @return the extracted UUID, or empty if the header is missing/malformed or has no
     *         recognizable claim
     */
    public Optional<UUID> extractUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = authorizationHeader.substring(7);
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(payloadBytes);

            String candidate = null;
            if (payload.hasNonNull("id")) {
                candidate = payload.get("id").asText();
            } else if (payload.hasNonNull("sub")) {
                candidate = payload.get("sub").asText();
            } else if (payload.hasNonNull("user_id")) {
                candidate = payload.get("user_id").asText();
            } else if (payload.hasNonNull("uid")) {
                candidate = payload.get("uid").asText();
            }

            if (candidate == null || candidate.isBlank()) {
                return Optional.empty();
            }

            try {
                return Optional.of(UUID.fromString(candidate));
            } catch (IllegalArgumentException e) {
                return Optional.of(UUID.nameUUIDFromBytes(candidate.getBytes(StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

}
