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
           "(:titleSearch IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', CAST(:titleSearch AS string), '%'))) AND " +
           "(:windowFilter IS NULL OR " +
           "  (CAST(:windowFilter AS string) = 'UPCOMING' AND p.startDate > :today) OR " +
           "  (CAST(:windowFilter AS string) = 'ACTIVE' AND :today BETWEEN p.startDate AND p.endDate) OR " +
           "  (CAST(:windowFilter AS string) = 'PAST' AND p.endDate < :today)) " +
           "ORDER BY p.startDate DESC")
    Page<Paper> findByFilters(
            @Param("titleSearch") String titleSearch,
            @Param("windowFilter") String windowFilter,
            @Param("today") LocalDate today,
            Pageable pageable
    );

    /**
     * Finds papers whose leaderboard is currently published — backs student-facing leaderboard
     * discovery.
     *
     * <p>A paper-event in a correlation has no leaderboard of its own; the correlation holds it,
     * so its publish flag is what counts. Such a correlation is also represented here exactly
     * once — by its earliest-created paper-event — because its sittings are one paper to a
     * student, and listing both would offer them the same leaderboard twice under one name.
     *
     * @param pageable pagination information
     * @return a page of papers with a published leaderboard, most recently started first
     */
    /**
     * The ids of every paper-event in a correlation — the sittings that together make up one real
     * paper.
     *
     * @param correlationId the correlation
     * @return its paper-events' ids
     */
    @Query("SELECT p.id FROM Paper p WHERE p.correlation.id = :correlationId")
    List<UUID> findIdsByCorrelationId(@Param("correlationId") UUID correlationId);

    /**
     * Every paper-event in a correlation.
     *
     * @param correlationId the correlation
     * @return its paper-events
     */
    List<Paper> findByCorrelationId(UUID correlationId);

    @Query("SELECT p FROM Paper p WHERE "
            + "(p.correlation IS NULL AND p.leaderboardPublished = true) OR "
            + "(p.correlation IS NOT NULL AND p.correlation.leaderboardPublished = true AND "
            + " p.createdAt = (SELECT MIN(m.createdAt) FROM Paper m WHERE m.correlation = p.correlation)) "
            + "ORDER BY p.startDate DESC")
    Page<Paper> findWithPublishedLeaderboard(Pageable pageable);

}
