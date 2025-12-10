package dopaminelite.payment_portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dopaminelite.payment_portal.dto.portal.BulkPortalVisibilityUpdateRequest;
import dopaminelite.payment_portal.dto.portal.PaymentPortalCreateRequest;
import dopaminelite.payment_portal.dto.portal.PaymentPortalUpdateRequest;
import dopaminelite.payment_portal.entity.PaymentPortal;
import dopaminelite.payment_portal.entity.enums.PortalVisibility;
import dopaminelite.payment_portal.repository.PaymentPortalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Payment Portal API Tests")
class PaymentPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentPortalRepository portalRepository;

    @BeforeEach
    void setUp() {
        portalRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/v1/portals - Should return empty list when no portals exist")
    void testListPortals_EmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/portals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/portals - Should return all portals with pagination")
    void testListPortals_WithPagination() throws Exception {
        // Create test data
        createTestPortal("portal-1", "Portal 1", 11, 2025, true);
        createTestPortal("portal-2", "Portal 2", 12, 2025, false);
        createTestPortal("portal-3", "Portal 3", 11, 2025, true);

        mockMvc.perform(get("/api/v1/portals")
                        .param("limit", "2")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/portals - Should filter by month and year")
    void testListPortals_FilterByMonthAndYear() throws Exception {
        createTestPortal("portal-nov-2025", "November 2025", 11, 2025, true);
        createTestPortal("portal-dec-2025", "December 2025", 12, 2025, true);
        createTestPortal("portal-nov-2024", "November 2024", 11, 2024, true);

        mockMvc.perform(get("/api/v1/portals")
                        .param("month", "11")
                        .param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("portal-nov-2025"))
                .andExpect(jsonPath("$.items[0].month").value(11))
                .andExpect(jsonPath("$.items[0].year").value(2025));
    }

    @Test
    @DisplayName("GET /api/v1/portals - Should filter by isPublished")
    void testListPortals_FilterByPublished() throws Exception {
        createTestPortal("published-portal", "Published", 11, 2025, true);
        createTestPortal("unpublished-portal", "Unpublished", 11, 2025, false);

        mockMvc.perform(get("/api/v1/portals")
                        .param("isPublished", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("published-portal"))
                .andExpect(jsonPath("$.items[0].isPublished").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/portals - Should create portal successfully")
    void testCreatePortal_Success() throws Exception {
        PaymentPortalCreateRequest request = new PaymentPortalCreateRequest();
        request.setMonth(11);
        request.setYear(2025);
        request.setName("test-portal");
        request.setDisplayName("Test Portal Display");
        request.setIsPublished(false);

        mockMvc.perform(post("/api/v1/portals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Admin-Id", UUID.randomUUID().toString())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("test-portal"))
                .andExpect(jsonPath("$.displayName").value("Test Portal Display"))
                .andExpect(jsonPath("$.month").value(11))
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.isPublished").value(false))
                .andExpect(jsonPath("$.visibility").value("HIDDEN"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("POST /api/v1/portals - Should create published portal with correct visibility")
    void testCreatePortal_Published() throws Exception {
        PaymentPortalCreateRequest request = new PaymentPortalCreateRequest();
        request.setMonth(12);
        request.setYear(2025);
        request.setName("published-test");
        request.setDisplayName("Published Test Portal");
        request.setIsPublished(true);

        mockMvc.perform(post("/api/v1/portals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPublished").value(true))
                .andExpect(jsonPath("$.visibility").value("PUBLISHED"));
    }

    @Test
    @DisplayName("POST /api/v1/portals - Should fail with duplicate portal name")
    void testCreatePortal_DuplicateName() throws Exception {
        createTestPortal("duplicate-portal", "Duplicate", 11, 2025, false);

        PaymentPortalCreateRequest request = new PaymentPortalCreateRequest();
        request.setMonth(11);
        request.setYear(2025);
        request.setName("duplicate-portal");
        request.setDisplayName("Duplicate Portal");

        mockMvc.perform(post("/api/v1/portals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value(containsString("duplicate-portal")));
    }

    @Test
    @DisplayName("POST /api/v1/portals - Should fail validation with missing required fields")
    void testCreatePortal_ValidationError() throws Exception {
        PaymentPortalCreateRequest request = new PaymentPortalCreateRequest();
        // Missing required fields

        mockMvc.perform(post("/api/v1/portals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @DisplayName("POST /api/v1/portals - Should fail validation with invalid month")
    void testCreatePortal_InvalidMonth() throws Exception {
        PaymentPortalCreateRequest request = new PaymentPortalCreateRequest();
        request.setMonth(13); // Invalid month
        request.setYear(2025);
        request.setName("test-portal");
        request.setDisplayName("Test Portal");

        mockMvc.perform(post("/api/v1/portals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/v1/portals/{portalId} - Should return portal by ID")
    void testGetPortalById_Success() throws Exception {
        PaymentPortal portal = createTestPortal("test-portal", "Test Portal", 11, 2025, true);

        mockMvc.perform(get("/api/v1/portals/{portalId}", portal.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(portal.getId().toString()))
                .andExpect(jsonPath("$.name").value("test-portal"))
                .andExpect(jsonPath("$.displayName").value("Test Portal"))
                .andExpect(jsonPath("$.month").value(11))
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.isPublished").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/portals/{portalId} - Should return 404 for non-existent portal")
    void testGetPortalById_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/portals/{portalId}", nonExistentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString(nonExistentId.toString())));
    }

    @Test
    @DisplayName("PATCH /api/v1/portals/{portalId} - Should update visibility")
    void testUpdatePortal_Visibility() throws Exception {
        PaymentPortal portal = createTestPortal("test-portal", "Test", 11, 2025, true);

        PaymentPortalUpdateRequest request = new PaymentPortalUpdateRequest();
        request.setVisibility(PortalVisibility.HIDDEN);

        mockMvc.perform(patch("/api/v1/portals/{portalId}", portal.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("HIDDEN"))
                .andExpect(jsonPath("$.isPublished").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/portals/{portalId} - Should return 404 for non-existent portal")
    void testUpdatePortal_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        PaymentPortalUpdateRequest request = new PaymentPortalUpdateRequest();
        request.setVisibility(PortalVisibility.HIDDEN);

        mockMvc.perform(patch("/api/v1/portals/{portalId}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/v1/portals/bulk-visibility - Should update multiple portals")
    void testBulkUpdateVisibility_Success() throws Exception {
        PaymentPortal portal1 = createTestPortal("portal-1", "Portal 1", 11, 2025, false);
        PaymentPortal portal2 = createTestPortal("portal-2", "Portal 2", 11, 2025, false);
        PaymentPortal portal3 = createTestPortal("portal-3", "Portal 3", 11, 2025, false);

        BulkPortalVisibilityUpdateRequest request = new BulkPortalVisibilityUpdateRequest();
        request.setPortalIds(Arrays.asList(portal1.getId(), portal2.getId()));
        request.setIsPublished(true);

        mockMvc.perform(patch("/api/v1/portals/bulk-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify updates
        mockMvc.perform(get("/api/v1/portals/{portalId}", portal1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublished").value(true))
                .andExpect(jsonPath("$.visibility").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/portals/{portalId}", portal2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublished").value(true));

        // Portal 3 should remain unchanged
        mockMvc.perform(get("/api/v1/portals/{portalId}", portal3.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublished").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/portals/bulk-visibility - Should fail with invalid portal IDs")
    void testBulkUpdateVisibility_InvalidIds() throws Exception {
        PaymentPortal portal1 = createTestPortal("portal-1", "Portal 1", 11, 2025, false);
        UUID invalidId = UUID.randomUUID();

        BulkPortalVisibilityUpdateRequest request = new BulkPortalVisibilityUpdateRequest();
        request.setPortalIds(Arrays.asList(portal1.getId(), invalidId));
        request.setIsPublished(true);

        mockMvc.perform(patch("/api/v1/portals/bulk-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/v1/portals/bulk-visibility - Should fail validation with empty list")
    void testBulkUpdateVisibility_EmptyList() throws Exception {
        BulkPortalVisibilityUpdateRequest request = new BulkPortalVisibilityUpdateRequest();
        request.setPortalIds(List.of());
        request.setIsPublished(true);

        mockMvc.perform(patch("/api/v1/portals/bulk-visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // Helper method to create test portals
    private PaymentPortal createTestPortal(String name, String displayName, int month, int year, boolean isPublished) {
        PaymentPortal portal = new PaymentPortal();
        portal.setName(name);
        portal.setDisplayName(displayName);
        portal.setMonth(month);
        portal.setYear(year);
        portal.setIsPublished(isPublished);
        portal.setVisibility(isPublished ? PortalVisibility.PUBLISHED : PortalVisibility.HIDDEN);
        portal.setCreatedByAdminId(UUID.randomUUID());
        return portalRepository.save(portal);
    }
}
