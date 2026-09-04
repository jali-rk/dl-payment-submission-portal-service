package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.external.BffObjectResponse;
import dopaminelite.payment_portal.dto.external.StudentLookupDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/**
 * Service for verifying a student's existence and fetching their public data from the BFF,
 * by code number. Used when an instructor enters a {@code PaperMark} — only a student who
 * resolves through this lookup can have marks recorded against them.
 *
 * <p>{@code /students/by-code-number} requires authentication on the BFF side, and this is a
 * server-to-server call with no end-user token to forward — so it authenticates the same way
 * the BFF authenticates to User Service: an {@code X-Service-Token} shared secret (BFF's
 * {@code authenticateToken} middleware accepts this as an alternative to a JWT).
 *
 * <p>Unlike {@link PaperCenterService}'s broad catch-and-return-empty-fallback, a failure here
 * that isn't a plain "not found" (5xx, timeout) is deliberately allowed to propagate rather
 * than being swallowed as "student doesn't exist" — an infra failure should surface as a
 * server error, not a misleading 404 to the instructor.
 */
@Service
public class StudentLookupService {

    private static final Logger logger = LoggerFactory.getLogger(StudentLookupService.class);
    private static final String SERVICE_NAME = "payment-portal-service";

    private final RestTemplate restTemplate;
    private final String bffBaseUrl;
    private final String internalServiceToken;

    public StudentLookupService(
            RestTemplate bffRestTemplate,
            @Value("${bff.base-url}") String bffBaseUrl,
            @Value("${bff.internal-service-token:}") String internalServiceToken) {
        this.restTemplate = bffRestTemplate;
        this.bffBaseUrl = bffBaseUrl;
        this.internalServiceToken = internalServiceToken;
    }

    /**
     * Looks up a student by their exact code number.
     *
     * @param codeNumber the student's code number, as typed by the instructor
     * @return the student's public data, or empty if no student has that code number
     * @throws org.springframework.web.client.RestClientException if the BFF call fails for any
     *         reason other than a 404 (e.g. 5xx, timeout) — not swallowed, propagates as a
     *         server error
     */
    public Optional<StudentLookupDto> findByCodeNumber(String codeNumber) {
        String url = UriComponentsBuilder.fromHttpUrl(bffBaseUrl + "/students/by-code-number")
                .queryParam("codeNumber", codeNumber)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Token", internalServiceToken);
        headers.set("X-Service-Name", SERVICE_NAME);

        try {
            ResponseEntity<BffObjectResponse<StudentLookupDto>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<BffObjectResponse<StudentLookupDto>>() {});
            BffObjectResponse<StudentLookupDto> envelope = response.getBody();
            StudentLookupDto student = envelope == null ? null : envelope.getData();
            if (student == null || student.getId() == null) {
                return Optional.empty();
            }
            return Optional.of(student);
        } catch (HttpClientErrorException.NotFound e) {
            logger.debug("No student found for code number: {}", codeNumber);
            return Optional.empty();
        }
    }

}
