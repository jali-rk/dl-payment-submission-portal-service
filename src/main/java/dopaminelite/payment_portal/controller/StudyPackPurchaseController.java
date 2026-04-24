package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.studypack.StudyPackPurchaseResponse;
import dopaminelite.payment_portal.entity.StudyPackPurchase;
import dopaminelite.payment_portal.mapper.StudyPackPurchaseMapper;
import dopaminelite.payment_portal.service.StudyPackPurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for managing study pack purchases.
 * Provides endpoints for retrieving purchase history.
 */
@Slf4j
@RestController
@RequestMapping("/study-pack-purchases")
@RequiredArgsConstructor
public class StudyPackPurchaseController {

    private final StudyPackPurchaseService studyPackPurchaseService;
    private final StudyPackPurchaseMapper studyPackPurchaseMapper;

    /**
     * Retrieves all purchases for a specific student.
     * This endpoint is called by the BFF with the student ID from the authenticated user.
     *
     * @param studentId the UUID of the student (passed from BFF via header or path)
     * @param statusFilter optional filter by purchase status (PENDING, COMPLETED, FAILED)
     * @return list of study pack purchases
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<StudyPackPurchaseResponse>> getStudentPurchases(
            @PathVariable UUID studentId,
            @RequestParam(required = false) String statusFilter
    ) {
        log.info("Fetching study pack purchases for student: {}, statusFilter: {}", studentId, statusFilter);
        
        List<StudyPackPurchase> purchases;
        
        // If status filter is provided and is "COMPLETED", fetch only completed purchases
        if ("COMPLETED".equalsIgnoreCase(statusFilter)) {
            purchases = studyPackPurchaseService.getCompletedPurchases(studentId);
        } else {
            // Otherwise, fetch all purchases
            purchases = studyPackPurchaseService.getStudentPurchases(studentId);
        }
        
        // Map entities to DTOs
        List<StudyPackPurchaseResponse> response = purchases.stream()
                .map(studyPackPurchaseMapper::toResponse)
                .collect(Collectors.toList());
        
        log.info("Found {} study pack purchases for student: {}", response.size(), studentId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Alternative endpoint that uses header-based authentication.
     * The BFF can pass the student ID via X-User-Id header.
     *
     * @param studentIdHeader the UUID of the student from header
     * @param statusFilter optional filter by purchase status
     * @return list of study pack purchases
     */
    @GetMapping("/my-purchases")
    public ResponseEntity<List<StudyPackPurchaseResponse>> getMyPurchases(
            @RequestHeader("X-User-Id") UUID studentIdHeader,
            @RequestParam(required = false) String statusFilter
    ) {
        log.info("Fetching my study pack purchases for student: {}, statusFilter: {}", studentIdHeader, statusFilter);
        
        List<StudyPackPurchase> purchases;
        
        if ("COMPLETED".equalsIgnoreCase(statusFilter)) {
            purchases = studyPackPurchaseService.getCompletedPurchases(studentIdHeader);
        } else {
            purchases = studyPackPurchaseService.getStudentPurchases(studentIdHeader);
        }
        
        List<StudyPackPurchaseResponse> response = purchases.stream()
                .map(studyPackPurchaseMapper::toResponse)
                .collect(Collectors.toList());
        
        log.info("Found {} study pack purchases for student: {}", response.size(), studentIdHeader);
        
        return ResponseEntity.ok(response);
    }
    
}
