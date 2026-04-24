package dopaminelite.payment_portal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of EnrollmentService that calls the Content Management Service
 * to enroll students in classes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {
    
    private final RestTemplate restTemplate;
    
    @Value("${services.content-management.url:http://localhost:8080}")
    private String contentServiceUrl;
    
    @Value("${services.content-management.internal-token:}")
    private String internalServiceToken;
    
    /**
     * Enrolls a student in a class by calling the Content Management Service.
     * Adds the student's ID to the folder's allowedEmails list.
     *
     * @param studentId the student UUID
     * @param classId the class/folder ID
     * @throws RuntimeException if the API call fails
     */
    @Override
    public void enrollStudent(UUID studentId, String classId) {
        log.info("Enrolling student {} in class {}", studentId, classId);
        
        try {
            // Prepare request to add student to folder
            String url = contentServiceUrl + "/folders/" + classId + "/emails";
            
            // Request body with student IDs to add
            Map<String, Object> requestBody = Map.of(
                "emails", List.of(studentId.toString())
            );
            
            // Set up headers with internal service token
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            if (internalServiceToken != null && !internalServiceToken.isEmpty()) {
                headers.set("X-Service-Token", internalServiceToken);
                headers.set("X-Service-Name", "payment-service");
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // Make API call
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully enrolled student {} in class {}", studentId, classId);
            } else {
                throw new RuntimeException("Enrollment API returned status: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Failed to enroll student {} in class {}", studentId, classId, e);
            throw new RuntimeException("Enrollment failed: " + e.getMessage(), e);
        }
    }
    
}
