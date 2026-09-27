package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.paper.CorrelationCreateRequest;
import dopaminelite.payment_portal.dto.paper.CorrelationResponse;
import dopaminelite.payment_portal.dto.paper.CorrelationUpdateRequest;
import dopaminelite.payment_portal.dto.paper.MarkSchemeDto;
import dopaminelite.payment_portal.dto.paper.PaperRefDto;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperCorrelation;
import dopaminelite.payment_portal.exception.DuplicateResourceException;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.exception.ValidationException;
import dopaminelite.payment_portal.repository.PaperCorrelationRepository;
import dopaminelite.payment_portal.repository.PaperMarkRepository;
import dopaminelite.payment_portal.repository.PaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Manages correlations: the groupings that say two paper-events are the same real paper, and that
 * own the mark scheme, marks and leaderboard on their behalf.
 *
 * <p>Creating and renaming one is unrestricted — paper-events reference a correlation by id, so a
 * rename can't break anything. Deleting is not: a correlation holds the marks and leaderboard for
 * every sitting in it, so it may only go once nothing depends on it.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaperCorrelationService {

    /**
     * Deliberately narrow. The code is typed by one admin and picked from a list by another, and
     * a code that differs only by case or a stray space would silently fail to group two sittings
     * — reintroducing the duplicate-QR bug this whole feature exists to fix.
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_-]+$");

    private final PaperCorrelationRepository correlationRepository;
    private final PaperRepository paperRepository;
    private final PaperMarkRepository paperMarkRepository;

    /**
     * Lists every correlation, for the picker shown when creating a paper-event.
     *
     * @return all correlations, alphabetically by code
     */
    public List<CorrelationResponse> listCorrelations() {
        return correlationRepository.findAllByOrderByCodeAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves a correlation by ID.
     *
     * @param id the correlation's ID
     * @return the correlation
     * @throws ResourceNotFoundException if no correlation exists with that ID
     */
    public CorrelationResponse getCorrelation(UUID id) {
        return toResponse(requireCorrelation(id));
    }

    /**
     * Creates a correlation. Its mark scheme starts empty and is set through whichever paper-event
     * joins it, so an admin never configures the same scheme twice.
     *
     * @param request the code and display name
     * @return the created correlation
     * @throws ValidationException if the code isn't in the required shape
     * @throws DuplicateResourceException if another correlation already uses that code
     */
    @Transactional
    public CorrelationResponse createCorrelation(CorrelationCreateRequest request) {
        String code = normalizeCode(request.getCode());
        requireCodeAvailable(code, null);

        PaperCorrelation correlation = new PaperCorrelation();
        correlation.setCode(code);
        correlation.setDisplayName(request.getDisplayName().trim());

        return toResponse(correlationRepository.save(correlation));
    }

    /**
     * Renames a correlation. Only non-null fields are applied.
     *
     * <p>Allowed at any point in a paper's life, including once it is running and marked: the code
     * is a label, and the grouping is held by id. Renaming is in fact the only way to fix a typo
     * in what students are shown.
     *
     * @param id the correlation's ID
     * @param request the fields to change
     * @return the updated correlation
     * @throws ResourceNotFoundException if no correlation exists with that ID
     * @throws ValidationException if a supplied code isn't in the required shape
     * @throws DuplicateResourceException if a supplied code is already used by another correlation
     */
    @Transactional
    public CorrelationResponse updateCorrelation(UUID id, CorrelationUpdateRequest request) {
        PaperCorrelation correlation = requireCorrelation(id);

        if (request.getCode() != null) {
            String code = normalizeCode(request.getCode());
            requireCodeAvailable(code, id);
            correlation.setCode(code);
        }
        if (request.getDisplayName() != null) {
            correlation.setDisplayName(request.getDisplayName().trim());
        }

        return toResponse(correlationRepository.save(correlation));
    }

    /**
     * Deletes a correlation, but only once nothing depends on it: no paper-event still points at
     * it, and no marks have been recorded against it.
     *
     * <p>Both conditions matter. Its paper-events would silently revert to owning their own
     * (empty) grading, and its marks — which belong to every sitting at once — would go with it.
     * So an admin detaches the paper-events first, which is itself only possible before any of
     * them has started.
     *
     * @param id the correlation's ID
     * @throws ResourceNotFoundException if no correlation exists with that ID
     * @throws ValidationException if paper-events or marks still depend on it
     */
    @Transactional
    public void deleteCorrelation(UUID id) {
        PaperCorrelation correlation = requireCorrelation(id);

        boolean hasPaperEvents = !paperRepository.findIdsByCorrelationId(id).isEmpty();
        if (hasPaperEvents || paperMarkRepository.countByOwnerId(id) > 0) {
            throw ValidationException.correlationHasDependents(id);
        }

        correlationRepository.delete(correlation);
    }

    private PaperCorrelation requireCorrelation(UUID id) {
        return correlationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Correlation not found with id: " + id));
    }

    /**
     * Uppercases and trims so the same code typed two ways is the same code, then rejects
     * anything left that could make two intended-identical codes differ.
     */
    private String normalizeCode(String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase();
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw ValidationException.invalidCorrelationCode(raw);
        }
        return code;
    }

    private void requireCodeAvailable(String code, UUID excludingId) {
        correlationRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(excludingId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("A correlation with code " + code + " already exists");
                });
    }

    private CorrelationResponse toResponse(PaperCorrelation correlation) {
        List<PaperRefDto> paperEvents = paperRepository.findByCorrelationId(correlation.getId()).stream()
                .map(this::toPaperRef)
                .toList();

        return new CorrelationResponse(
                correlation.getId(),
                correlation.getCode(),
                correlation.getDisplayName(),
                new MarkSchemeDto(
                        correlation.getMcqMaxMarks(),
                        correlation.getStructuredMaxMarks(),
                        correlation.getEssayMaxMarks()),
                correlation.isLeaderboardPublished(),
                correlation.getLeaderboardLastGeneratedAt(),
                paperEvents);
    }

    private PaperRefDto toPaperRef(Paper paper) {
        return new PaperRefDto(paper.getId(), paper.getTitle(), paper.getStartDate(), paper.getEndDate());
    }

}
