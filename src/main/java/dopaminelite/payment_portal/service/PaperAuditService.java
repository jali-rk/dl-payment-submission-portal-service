package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.entity.PaperAuditLog;
import dopaminelite.payment_portal.entity.PaperCorrelation;
import dopaminelite.payment_portal.entity.enums.PaperAuditAction;
import dopaminelite.payment_portal.repository.PaperAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Records the decisions made about a paper-event: when it was created, grouped with another
 * sitting, re-pointed at a different payment portal, re-windowed, re-marked, or deleted.
 *
 * <p>Failures here are logged and swallowed. Losing an audit row is bad; failing the admin's
 * actual change because the record of it couldn't be written would be worse, and would make the
 * trail itself a source of outages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperAuditService {

    private final PaperAuditLogRepository auditLogRepository;

    /**
     * Appends an entry to a paper-event's trail.
     *
     * @param paperId the paper-event the decision was about
     * @param correlation the correlation involved, where the action concerns one
     * @param action what was decided
     * @param details a compact "old -> new" summary, or null where the action says it all
     * @param actorId the admin responsible, or null if the request carried no usable token
     */
    public void record(UUID paperId, PaperCorrelation correlation, PaperAuditAction action,
                       String details, UUID actorId) {
        try {
            PaperAuditLog entry = new PaperAuditLog();
            entry.setPaperId(paperId);
            entry.setCorrelationId(correlation == null ? null : correlation.getId());
            entry.setAction(action);
            entry.setDetails(details);
            entry.setActorId(actorId);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to record paper audit entry {} for paper {}: {}",
                    action, paperId, e.getMessage(), e);
        }
    }

}
