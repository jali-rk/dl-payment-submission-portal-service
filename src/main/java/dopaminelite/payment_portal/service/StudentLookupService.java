package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.external.BffObjectResponse;
import dopaminelite.payment_portal.dto.external.StudentLookupDto;
import jakarta.annotation.PostConstruct;
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
import org.springframework.web.client.RestClientException;
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
     * Logs the resolved BFF URL (and whether a service token is even configured) once at
     * startup — deliberately at INFO so it shows up in normal deployed logs without needing
     * debug level enabled. A misconfigured {@code bff.base-url}/{@code bff.internal-service-token}
     * (e.g. still defaulting to {@code http://localhost:3000}, or an empty token) is otherwise
     * invisible until the first real student lookup fails with an opaque 500.
     */
    @PostConstruct
    void logResolvedConfig() {
        logger.info("StudentLookupService: resolved bff.base-url={}, internal-service-token configured={}",
                bffBaseUrl, internalServiceToken != null && !internalServiceToken.isBlank());
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
        } catch (RestClientException e) {
            // Deliberately still rethrown uncaught (see class javadoc) — this is only about
            // making the cause visible in logs before it surfaces as a generic 500, instead of
            // an ops person having nothing to go on but "an unexpected error occurred". A 401/403
            // here almost always means bff.internal-service-token doesn't match the BFF's own
            // INTERNAL_SERVICE_TOKEN; a connection failure almost always means bff.base-url
            // isn't reachable from this service in this environment.
            logger.error("StudentLookupService: BFF call failed calling {} (bff.base-url={}): {}",
                    url, bffBaseUrl, e.getMessage());
            throw e;
        }
    }

}
