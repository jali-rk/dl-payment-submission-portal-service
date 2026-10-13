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

    /**
     * Papers a student can open the leaderboard of: one entry per real paper.
     *
     * <p>Which flag counts depends on who owns the grading. A standalone paper-event is published
     * by its own flag; a paper-event in a correlation by the correlation's, whatever its own flag
     * says. A correlation appears once, represented by the paper-event created first — the
     * leaderboard resolves to the correlation from any of them, so which one stands in doesn't
     * matter, only that it is exactly one.
     *
     * <p>The correlation is joined with a LEFT JOIN on purpose. Reading
     * {@code p.correlation.leaderboardPublished} straight in the WHERE makes Hibernate inner join
     * the correlations table, which silently discards every paper-event that has no correlation —
     * so no standalone paper ever listed, even with no correlation in existence.
     *
     * <p>Ties on creation time are broken by id, so two paper-events created in the same instant
     * can never both stand in for one correlation.
     *
     * @param pageable pagination information
     * @return a page of paper-events, most recently started first
     */
    @Query("SELECT p FROM Paper p LEFT JOIN p.correlation c WHERE "
            + "(c IS NULL AND p.leaderboardPublished = true) OR "
            + "(c IS NOT NULL AND c.leaderboardPublished = true AND NOT EXISTS ("
            + "   SELECT 1 FROM Paper o WHERE o.correlation = c AND "
            + "   (o.createdAt < p.createdAt OR (o.createdAt = p.createdAt AND o.id < p.id)))) "
            + "ORDER BY p.startDate DESC")
    Page<Paper> findWithPublishedLeaderboard(Pageable pageable);

    /**
     * The overall date range of each given correlation: earliest start to latest end across all of
     * its paper-events. One query for the whole page of a list rather than one per row.
     *
     * @param correlationIds the correlations to measure
     * @return one row per correlation that has paper-events
     */
    @Query("SELECT p.correlation.id AS correlationId, MIN(p.startDate) AS startDate, MAX(p.endDate) AS endDate "
            + "FROM Paper p WHERE p.correlation.id IN :correlationIds GROUP BY p.correlation.id")
    List<CorrelationDateSpan> findDateSpans(@Param("correlationIds") List<UUID> correlationIds);

    interface CorrelationDateSpan {
        UUID getCorrelationId();

        LocalDate getStartDate();

        LocalDate getEndDate();
    }

}
