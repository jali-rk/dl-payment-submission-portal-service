package dopaminelite.payment_portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.studypack.StudyPackCreateRequest;
import dopaminelite.payment_portal.dto.studypack.StudyPackResponse;
import dopaminelite.payment_portal.dto.studypack.StudyPackUpdateRequest;
import dopaminelite.payment_portal.service.StudyPackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * REST controller for managing study packs.
 * Provides endpoints for creating and retrieving study packs.
 */
@RestController
@RequestMapping("/study-packs")
@RequiredArgsConstructor
public class StudyPackController {

    private final StudyPackService studyPackService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new study pack.
     *
     * @param requestJson JSON string containing study pack data
     * @param thumbnail optional thumbnail image file
     * @return the created study pack with HTTP 201 status
     */
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<StudyPackResponse> createStudyPack(
            @RequestParam("request") String requestJson,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail
    ) {
        try {
            StudyPackCreateRequest request = objectMapper.readValue(requestJson, StudyPackCreateRequest.class);
            StudyPackResponse response = studyPackService.createStudyPack(request, thumbnail);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Lists all study packs with optional filtering and pagination.
     *
     * @param isActive filter by active status, optional
     * @param limit maximum number of results per page, defaults to 20
     * @param offset number of results to skip, defaults to 0
     * @return paginated list of study packs
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<StudyPackResponse>> listStudyPacks(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        // Validate limit range
        if (limit < 1 || limit > 100) {
            limit = 20;
        }
        
        PaginatedResponse<StudyPackResponse> response = studyPackService.listStudyPacks(
                isActive, limit, offset
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing study pack.
     *
     * @param id the study pack ID
     * @param requestJson JSON string containing update data
     * @param thumbnail optional new thumbnail image file
     * @return the updated study pack
     */
    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<StudyPackResponse> updateStudyPack(
            @PathVariable UUID id,
            @RequestParam("request") String requestJson,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail
    ) {
        try {
            StudyPackUpdateRequest request = objectMapper.readValue(requestJson, StudyPackUpdateRequest.class);
            StudyPackResponse response = studyPackService.updateStudyPack(id, request, thumbnail);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Deletes a study pack.
     * Performs soft delete if students have purchased it, otherwise hard delete.
     *
     * @param id the study pack ID
     * @return no content response
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudyPack(@PathVariable UUID id) {
        studyPackService.deleteStudyPack(id);
        return ResponseEntity.noContent().build();
    }
    
}
