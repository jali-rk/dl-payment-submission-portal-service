package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaperMark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PaperMark entity operations.
 *
 * <p>Every lookup here is keyed by <em>owner</em> id rather than paper id: a mark belongs either to
 * a standalone paper-event or to the {@code PaperCorrelation} grouping several paper-events of the
 * same real paper (see {@code MarkOwner}). Ids are UUIDs from one sequence space, so a single
 * {@code ownerId} parameter matched against both sides is unambiguous — and it keeps this
 * repository at one method per question instead of two parallel sets.
 */
@Repository
public interface PaperMarkRepository extends JpaRepository<PaperMark, UUID> {

    @Query("SELECT COUNT(m) > 0 FROM PaperMark m "
            + "WHERE (m.paper.id = :ownerId OR m.correlation.id = :ownerId) AND m.studentId = :studentId")
    boolean existsByOwnerIdAndStudentId(@Param("ownerId") UUID ownerId, @Param("studentId") UUID studentId);

    /**
     * Counts marks for an owner — used to determine whether its mark scheme is locked, and
     * whether a paper-event may still be deleted or moved into a correlation.
     *
     * @param ownerId the paper-event's or correlation's ID
     * @return the number of marks recorded against it
     */
    @Query("SELECT COUNT(m) FROM PaperMark m WHERE m.paper.id = :ownerId OR m.correlation.id = :ownerId")
    long countByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * Counts marks belonging to a paper-event <em>itself</em>, ignoring any correlation it may be
     * part of. Distinct from {@link #countByOwnerId} and needed when deciding whether a
     * paper-event can join a correlation: marks of its own would be orphaned by the move, whereas
     * the correlation's existing marks are no obstacle.
     *
     * @param paperId the paper-event's ID
     * @return the number of marks recorded directly against that paper-event
     */
    long countByPaperId(UUID paperId);

    /**
     * Scopes a single-mark lookup to the owner it's expected to belong to, so a {@code markId}
     * from a different paper 404s instead of silently succeeding.
     *
     * @param id the mark's ID
     * @param ownerId the paper-event or correlation it must belong to
     * @return the mark, if found and owned by that owner
     */
    @Query("SELECT m FROM PaperMark m "
            + "WHERE m.id = :id AND (m.paper.id = :ownerId OR m.correlation.id = :ownerId)")
    Optional<PaperMark> findByIdAndOwnerId(@Param("id") UUID id, @Param("ownerId") UUID ownerId);

    /**
     * The marks page's list query: an owner's marks, optionally narrowed to one instructor's own
     * entries and/or to a single student's code number. Both owning sides are eagerly fetched so
     * the mapper never triggers a lazy load.
     *
     * <p>The filters are optional rather than split across four methods, following
     * {@code PaperRepository.findByFilters}. Code number is matched in full, not as a substring —
     * an instructor types a whole code to pull up one student — and case-insensitively, so a code
     * typed in lower case still finds its mark.
     *
     * <p>Ordering carries {@code m.id} as a tiebreaker: marks recorded in the same instant would
     * otherwise come back in an arbitrary order, letting a row shift between pages and so appear
     * twice or not at all while paging.
     *
     * @param ownerId the paper-event's or correlation's ID
     * @param instructorId only this instructor's entries, or null for every instructor's
     * @param studentCodeNumber only this student's mark, or null for every student's; expected
     *        already trimmed, since a stray space would match nothing
     * @param pageable pagination information
     * @return a page of matching marks
     */
    @Query("SELECT m FROM PaperMark m LEFT JOIN FETCH m.paper LEFT JOIN FETCH m.correlation "
            + "WHERE (m.paper.id = :ownerId OR m.correlation.id = :ownerId) "
            + "AND (:instructorId IS NULL OR m.enteredByInstructorId = :instructorId) "
            + "AND (:studentCodeNumber IS NULL OR LOWER(m.studentSnapshot.codeNumber) "
            + "     = LOWER(CAST(:studentCodeNumber AS string))) "
            + "ORDER BY m.createdAt DESC, m.id ASC")
    Page<PaperMark> findByFilters(
            @Param("ownerId") UUID ownerId,
            @Param("instructorId") UUID instructorId,
            @Param("studentCodeNumber") String studentCodeNumber,
            Pageable pageable);

    /**
     * All marks for an owner ordered by total marks descending — the input ordering for
     * leaderboard rank generation.
     *
     * @param ownerId the paper-event's or correlation's ID
     * @return every mark for it, highest total first
     */
    @Query("SELECT m FROM PaperMark m "
            + "WHERE m.paper.id = :ownerId OR m.correlation.id = :ownerId ORDER BY m.totalMarks DESC")
    List<PaperMark> findByOwnerIdOrderByTotalMarksDesc(@Param("ownerId") UUID ownerId);

    /**
     * A page of marks for an owner that currently have a leaderboard rank assigned, ordered by
     * that rank — the leaderboard read query, paginated since a paper can realistically have
     * thousands of ranked students. Marks entered after the most recent "Generate Ranks" run have
     * a null rank and are naturally excluded. Secondarily ordered by ID so tied ranks (which share
     * the same rank number) come back in a stable order across repeated reads, rather than
     * whatever order the database happens to return them in.
     *
     * @param ownerId the paper-event's or correlation's ID
     * @param pageable pagination information
     * @return a page of the current leaderboard entries, best rank first, plus the total count
     */
    @Query("SELECT m FROM PaperMark m "
            + "WHERE (m.paper.id = :ownerId OR m.correlation.id = :ownerId) AND m.rank IS NOT NULL "
            + "ORDER BY m.rank ASC, m.id ASC")
    Page<PaperMark> findRankedByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

    /**
     * The calling student's own ranked mark, regardless of where it falls in the leaderboard's
     * page ordering — lets the leaderboard surface "your rank" without the caller needing to page
     * through potentially thousands of entries to find themselves.
     *
     * @param ownerId the paper-event's or correlation's ID
     * @param studentId the caller's ID
     * @return the caller's ranked mark, if they have one
     */
    @Query("SELECT m FROM PaperMark m "
            + "WHERE (m.paper.id = :ownerId OR m.correlation.id = :ownerId) "
            + "AND m.studentId = :studentId AND m.rank IS NOT NULL")
    Optional<PaperMark> findRankedByOwnerIdAndStudentId(@Param("ownerId") UUID ownerId,
                                                        @Param("studentId") UUID studentId);

    /**
     * Every mark recorded for a student, across all papers, newest paper first — backs the
     * unified academic profile's "all of this student's papers" view.
     *
     * <p>Both owning sides are LEFT JOIN FETCHed, not inner-joined: a correlation-owned mark has no
     * {@code paper} at all, and an inner join would silently drop it from the student's profile
     * entirely. Ordering falls back to the earliest start date among the correlation's
     * paper-events, which is the date that paper was actually first sat.
     *
     * @param studentId the student's ID
     * @return the student's marks, most recent paper first; empty if they have none yet
     */
    @Query("SELECT m FROM PaperMark m "
            + "LEFT JOIN FETCH m.paper p "
            + "LEFT JOIN FETCH m.correlation c "
            + "WHERE m.studentId = :studentId "
            + "ORDER BY COALESCE(p.startDate, "
            + "  (SELECT MIN(cp.startDate) FROM Paper cp WHERE cp.correlation = c)) DESC")
    List<PaperMark> findByStudentId(@Param("studentId") UUID studentId);

}
