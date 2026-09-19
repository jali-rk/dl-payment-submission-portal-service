package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.entity.MarkOwner;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.repository.PaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Answers the one question every marks, mark-scheme and leaderboard operation has to ask first:
 * given a paper-event, what actually owns its grading?
 *
 * <p>Usually the paper-event itself. But when it belongs to a {@code PaperCorrelation} — because
 * the same real paper was created twice with different windows for different payment portals — the
 * correlation owns the marks and the leaderboard, so an instructor marking either sitting is
 * marking one paper and an admin opening either sees the same results.
 *
 * <p>This exists so the REST surface can stay paper-scoped. Callers keep passing the paper-event id
 * they already have, resolve once at the top of the method, and work against {@link MarkOwner}
 * from there without branching.
 */
@Component
@RequiredArgsConstructor
public class MarkOwnerResolver {

    private final PaperRepository paperRepository;

    /**
     * Resolves the owner of a paper-event's grading.
     *
     * @param paperId the paper-event's ID
     * @return its correlation if it has one, otherwise the paper-event itself
     * @throws ResourceNotFoundException if no paper exists with that ID
     */
    public MarkOwner resolve(UUID paperId) {
        return ownerOf(paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId)));
    }

    /**
     * Resolves the owner of an already-loaded paper-event, for callers that need the paper itself
     * as well and shouldn't read it twice.
     *
     * @param paper the paper-event
     * @return its correlation if it has one, otherwise the paper-event itself
     */
    public MarkOwner ownerOf(Paper paper) {
        return paper.getCorrelation() != null ? paper.getCorrelation() : paper;
    }

}
