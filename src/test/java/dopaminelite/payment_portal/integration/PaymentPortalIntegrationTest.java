package dopaminelite.payment_portal.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dopaminelite.payment_portal.dto.portal.PaymentPortalCreateRequest;
import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

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

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCreateAndRetrievePortal() throws Exception {
        // Create portal
        PaymentPortalCreateRequest createRequest = new PaymentPortalCreateRequest();
        createRequest.setName("Integration Test Portal");
        createRequest.setDisplayName("Integration Test Portal Display");
        createRequest.setMonth(12);
        createRequest.setYear(2025);
        createRequest.setIsPublished(true);

        String createResponse = mockMvc.perform(post("/api/v1/portals")
                .contentType(MediaType.APPLICATION_JSON)
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
    @WithMockUser(username = "student", roles = {"STUDENT"})
    void testStudentCannotCreatePortal() throws Exception {
        PaymentPortalCreateRequest createRequest = new PaymentPortalCreateRequest();
        createRequest.setName("Unauthorized Portal");
        createRequest.setDisplayName("Unauthorized Portal Display");
        createRequest.setMonth(1);
        createRequest.setYear(2025);

        mockMvc.perform(post("/api/v1/portals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testListPortalsWithPagination() throws Exception {
        mockMvc.perform(get("/api/v1/portals")
                .param("limit", "10")
                .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.limit").value(10))
                .andExpect(jsonPath("$.pagination.offset").value(0));
    }

    @Test
    void testHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testSwaggerUiIsAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCreatePortalWithInvalidData() throws Exception {
        PaymentPortalCreateRequest invalidRequest = new PaymentPortalCreateRequest();
        invalidRequest.setName(""); // Invalid: empty name
        invalidRequest.setDisplayName(""); // Invalid: empty displayName
        invalidRequest.setMonth(13); // Invalid: month > 12
        invalidRequest.setYear(2025);

        mockMvc.perform(post("/api/v1/portals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
}
