package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.paper.CorrelationCreateRequest;
import dopaminelite.payment_portal.dto.paper.CorrelationResponse;
import dopaminelite.payment_portal.dto.paper.CorrelationUpdateRequest;
import dopaminelite.payment_portal.service.PaperCorrelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for correlations — the groupings that say two paper-events are the same real
 * paper. Role gating lives at the BFF, which restricts every write here to a MAIN_ADMIN.
 */
@RestController
@RequestMapping("/correlations")
@RequiredArgsConstructor
public class PaperCorrelationController {

    private final PaperCorrelationService correlationService;

    /**
     * Lists every correlation — backs the picker shown when creating a paper-event.
     *
     * @return all correlations, alphabetically by code
     */
    @GetMapping
    public ResponseEntity<List<CorrelationResponse>> listCorrelations() {
        return ResponseEntity.ok(correlationService.listCorrelations());
    }

    /**
     * Retrieves a single correlation, including which paper-events belong to it.
     *
     * @param correlationId the correlation's ID
     * @return the correlation
     */
    @GetMapping("/{correlationId}")
    public ResponseEntity<CorrelationResponse> getCorrelation(@PathVariable UUID correlationId) {
        return ResponseEntity.ok(correlationService.getCorrelation(correlationId));
    }

    /**
     * Creates a correlation.
     *
     * @param request the code and display name
     * @return the created correlation with HTTP 201 status
     */
    @PostMapping
    public ResponseEntity<CorrelationResponse> createCorrelation(
            @Valid @RequestBody CorrelationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(correlationService.createCorrelation(request));
    }

    /**
     * Renames a correlation. Only non-null fields are applied.
     *
     * @param correlationId the correlation's ID
     * @param request the fields to change
     * @return the updated correlation
     */
    @PatchMapping("/{correlationId}")
    public ResponseEntity<CorrelationResponse> updateCorrelation(
            @PathVariable UUID correlationId,
            @Valid @RequestBody CorrelationUpdateRequest request) {
        return ResponseEntity.ok(correlationService.updateCorrelation(correlationId, request));
    }

    /**
     * Deletes a correlation that nothing depends on any more.
     *
     * @param correlationId the correlation's ID
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{correlationId}")
    public ResponseEntity<Void> deleteCorrelation(@PathVariable UUID correlationId) {
        correlationService.deleteCorrelation(correlationId);
        return ResponseEntity.noContent().build();
    }

}
