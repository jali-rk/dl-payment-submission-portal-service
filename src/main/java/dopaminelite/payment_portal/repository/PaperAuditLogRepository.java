package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaperAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for PaperAuditLog entity operations.
 *
 * <p>Write-mostly. There is no read endpoint yet by design — the trail is queried directly when
 * something needs explaining — so the finder here exists for tests and for whatever surfaces it
 * later.
 */
@Repository
public interface PaperAuditLogRepository extends JpaRepository<PaperAuditLog, UUID> {

    List<PaperAuditLog> findByPaperIdOrderByCreatedAtAsc(UUID paperId);

}
