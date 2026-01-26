package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.external.BffStandardResponse;
import dopaminelite.payment_portal.dto.external.PaperCenterDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for integrating with BFF to retrieve paper center information.
 * Provides methods to fetch paper center data and create lookup maps.
 */
@Service
public class PaperCenterService {

    private static final Logger logger = LoggerFactory.getLogger(PaperCenterService.class);

    private final RestTemplate restTemplate;
    private final String bffBaseUrl;

    public PaperCenterService(
            RestTemplate bffRestTemplate,
            @Value("${bff.base-url}") String bffBaseUrl) {
        this.restTemplate = bffRestTemplate;
        this.bffBaseUrl = bffBaseUrl;
    }

    /**
     * Fetches all paper centers from BFF and returns a map of paper center ID to name.
     * If the BFF call fails, logs an error and returns an empty map.
     *
     * @return Map of paper center ID to paper center name
     */
    public Map<String, String> getPaperCenterNameMap() {
        try {
            String url = bffBaseUrl + "/paper-centers";
            logger.debug("Fetching paper centers from BFF: {}", url);

            ResponseEntity<BffStandardResponse<PaperCenterDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<BffStandardResponse<PaperCenterDto>>() {}
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                BffStandardResponse.ResponseData<PaperCenterDto> data = response.getBody().getData();
                if (data != null && data.getItems() != null) {
                    Map<String, String> paperCenterMap = data.getItems().stream()
                            .collect(Collectors.toMap(
                                    PaperCenterDto::getId,
                                    PaperCenterDto::getName,
                                    (existing, replacement) -> existing // handle duplicate keys
                            ));
                    logger.info("Successfully fetched {} paper centers from BFF", paperCenterMap.size());
                    return paperCenterMap;
                }
            }

            logger.warn("BFF returned empty or unsuccessful response for paper centers");
            return Collections.emptyMap();

        } catch (Exception e) {
            logger.error("Failed to fetch paper centers from BFF: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
