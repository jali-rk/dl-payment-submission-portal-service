package dopaminelite.payment_portal.controller;

import dopaminelite.payment_portal.dto.paper.LeaderboardVisibilityUpdateRequest;
import dopaminelite.payment_portal.dto.paper.PaperLeaderboardResponse;
import dopaminelite.payment_portal.service.PaperMarkService;
import dopaminelite.payment_portal.util.JwtUserIdExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for a paper's leaderboard: reading it (role-gated on the published flag),
 * regenerating its ranks, and toggling its visibility. Independent of {@code PaperMarkController}
 * even though both operate over {@code PaperMark} rows — this controller's endpoints have a
 * genuinely different access model (every role reads the same path; only some can write).
 */
@RestController
@RequestMapping("/papers/{paperId}/leaderboard")
@RequiredArgsConstructor
public class PaperLeaderboardController {

    private static final UUID UNKNOWN_CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final PaperMarkService paperMarkService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * Retrieves a page of a paper's leaderboard. Visible to instructors/admins/main admins
     * regardless of publish state; visible to any other/absent role only once published.
     * Always includes the caller's own ranked entry (if any) independent of paging, via {@code
     * callerEntry} in the response — the caller's ID is read from the same bearer token every
     * other endpoint here uses for attribution, not passed explicitly.
     *
     * @param paperId the UUID of the paper
     * @param role the caller's role, forwarded by the BFF
     * @param limit maximum number of entries per page, defaults to 50
     * @param offset number of entries to skip, defaults to 0
     * @param authorizationHeader the caller's bearer token, used to find their own ranked entry
     * @return the leaderboard page
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID, or the caller isn't privileged and the leaderboard isn't published
     */
    @GetMapping
    public ResponseEntity<PaperLeaderboardResponse> getLeaderboard(
            @PathVariable UUID paperId,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        if (limit < 1 || limit > 200) {
            limit = 50;
        }

        UUID callerId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(null);
        PaperLeaderboardResponse response = paperMarkService.getLeaderboard(paperId, role, callerId, limit, offset);
        return ResponseEntity.ok(response);
    }

    /**
     * Recomputes leaderboard ranks from the paper's currently recorded marks.
     *
     * @param paperId the UUID of the paper
     * @param authorizationHeader the caller's bearer token, used to attribute who generated the ranks
     * @return the refreshed leaderboard
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID
     * @throws dopaminelite.payment_portal.exception.ValidationException if the paper has no marks recorded yet
     */
    @PostMapping("/generate")
    public ResponseEntity<PaperLeaderboardResponse> generateRanks(
            @PathVariable UUID paperId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        UUID callerId = jwtUserIdExtractor.extractUserId(authorizationHeader).orElse(UNKNOWN_CALLER_ID);
        PaperLeaderboardResponse response = paperMarkService.generateRanks(paperId, callerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Publishes or hides a paper's leaderboard.
     *
     * @param paperId the UUID of the paper
     * @param request the new visibility state
     * @return the updated leaderboard
     * @throws dopaminelite.payment_portal.exception.ResourceNotFoundException if no paper exists with the given ID
     * @throws dopaminelite.payment_portal.exception.ValidationException if attempting to publish before ranks have ever been generated
     */
    @PatchMapping("/visibility")
    public ResponseEntity<PaperLeaderboardResponse> setLeaderboardVisibility(
            @PathVariable UUID paperId,
            @Valid @RequestBody LeaderboardVisibilityUpdateRequest request
    ) {
        PaperLeaderboardResponse response = paperMarkService.setLeaderboardVisibility(paperId, request);
        return ResponseEntity.ok(response);
    }

}
