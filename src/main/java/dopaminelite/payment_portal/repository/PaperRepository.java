package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.Paper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Paper entity operations.
 */
@Repository
public interface PaperRepository extends JpaRepository<Paper, UUID> {

    /**
     * Finds all papers linked to a given payment portal. Used on the approval hot path to
     * determine which papers should generate a slot for a newly-approved submission.
     *
     * @param portalId the payment portal's UUID
     * @return papers linked to that portal
     */
    @Query("SELECT DISTINCT p FROM Paper p JOIN p.linkedPortals lp WHERE lp.id = :portalId")
    List<Paper> findByLinkedPortalId(@Param("portalId") UUID portalId);

    /**
     * Finds papers matching optional title-search and validity-window filters, filtered
     * entirely in SQL so pagination counts stay correct.
     *
     * @param titleSearch case-insensitive substring match on title, null for no filtering
     * @param windowFilter one of UPCOMING/ACTIVE/PAST (as its enum name), null for no filtering
     * @param today the current date
     * @param pageable pagination information
     * @return a page of matching papers
     */
    @Query("SELECT p FROM Paper p WHERE " +
           "(:titleSearch IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :titleSearch, '%'))) AND " +
           "(:windowFilter IS NULL OR " +
           "  (:windowFilter = 'UPCOMING' AND p.startDate > :today) OR " +
           "  (:windowFilter = 'ACTIVE' AND :today BETWEEN p.startDate AND p.endDate) OR " +
           "  (:windowFilter = 'PAST' AND p.endDate < :today)) " +
           "ORDER BY p.startDate DESC")
    Page<Paper> findByFilters(
            @Param("titleSearch") String titleSearch,
            @Param("windowFilter") String windowFilter,
            @Param("today") LocalDate today,
            Pageable pageable
    );

}
