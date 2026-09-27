package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaperCorrelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PaperCorrelation entity operations.
 */
@Repository
public interface PaperCorrelationRepository extends JpaRepository<PaperCorrelation, UUID> {

    /**
     * Looks up a correlation by its admin-facing code, for the uniqueness check when one is
     * created or renamed. Codes are stored uppercase, so callers must uppercase before comparing.
     *
     * @param code the code to look for
     * @return the correlation with that code, if one exists
     */
    Optional<PaperCorrelation> findByCode(String code);

    /**
     * Every correlation, for the picker shown when creating a paper-event.
     *
     * @return all correlations, alphabetically by code
     */
    List<PaperCorrelation> findAllByOrderByCodeAsc();

}
