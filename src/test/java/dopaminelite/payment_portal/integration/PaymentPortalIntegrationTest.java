package dopaminelite.payment_portal.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dopaminelite.payment_portal.dto.portal.PaymentPortalCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentPortalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Setup runs before each test
    }

    private String generateTestJwtToken(java.util.UUID userId) {
        // Create a simple JWT token for testing (Base64 encoded)
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"" + userId.toString() + "\"}";
        String encodedHeader = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes());
        String encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
        String signature = "test-signature";
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    @Test
    void testCreateAndRetrievePortal() throws Exception {
        // Create portal
        PaymentPortalCreateRequest createRequest = new PaymentPortalCreateRequest();
        createRequest.setName("Integration Test Portal");
        createRequest.setDisplayName("Integration Test Portal Display");
        createRequest.setMonth(12);
        createRequest.setYear(2025);
        createRequest.setIsPublished(true);

        java.util.UUID adminId = java.util.UUID.randomUUID();
        String token = generateTestJwtToken(adminId);
        
        String createResponse = mockMvc.perform(post("/api/v1/portals")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Integration Test Portal"))
                .andExpect(jsonPath("$.displayName").value("Integration Test Portal Display"))
                .andExpect(jsonPath("$.month").value(12))
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.isPublished").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract ID from create response
        String portalId = objectMapper.readTree(createResponse).get("id").asText();

        // Retrieve portal by ID
        mockMvc.perform(get("/api/v1/portals/" + portalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(portalId))
                .andExpect(jsonPath("$.name").value("Integration Test Portal"))
                .andExpect(jsonPath("$.displayName").value("Integration Test Portal Display"))
                .andExpect(jsonPath("$.isPublished").value(true));
    }

    @Test
    void testListPortalsWithPagination() throws Exception {
        mockMvc.perform(get("/api/v1/portals")
                .param("limit", "10")
                .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").exists());
    }

    @Test
    void testCreatePortalWithInvalidData() throws Exception {
        PaymentPortalCreateRequest invalidRequest = new PaymentPortalCreateRequest();
        invalidRequest.setName(""); // Invalid: empty name
        invalidRequest.setDisplayName(""); // Invalid: empty displayName
        invalidRequest.setMonth(13); // Invalid: month > 12
        invalidRequest.setYear(2025);

        java.util.UUID adminId = java.util.UUID.randomUUID();
        String token = generateTestJwtToken(adminId);
        
        mockMvc.perform(post("/api/v1/portals")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
