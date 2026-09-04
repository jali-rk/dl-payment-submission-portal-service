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
 */
@Repository
public interface PaperMarkRepository extends JpaRepository<PaperMark, UUID> {

    boolean existsByPaperIdAndStudentId(UUID paperId, UUID studentId);

    /**
     * Counts marks for a paper — used to determine whether the paper's mark scheme is locked.
     *
     * @param paperId the paper's ID
     * @return the number of marks recorded for that paper
     */
    long countByPaperId(UUID paperId);

    /**
     * Scopes a single-mark lookup to the paper it's expected to belong to, so a {@code markId}
     * from a different paper 404s instead of silently succeeding.
     *
     * @param id the mark's ID
     * @param paperId the paper it must belong to
     * @return the mark, if found and owned by that paper
     */
    Optional<PaperMark> findByIdAndPaperId(UUID id, UUID paperId);

    /**
     * Finds marks for a paper, eagerly fetching the owning paper so the mapper never triggers
     * a lazy load.
     *
     * @param paperId the paper's ID
     * @param pageable pagination information
     * @return a page of matching marks
     */
    @Query("SELECT m FROM PaperMark m JOIN FETCH m.paper WHERE m.paper.id = :paperId ORDER BY m.createdAt DESC")
    Page<PaperMark> findByPaperId(@Param("paperId") UUID paperId, Pageable pageable);

    /**
     * All marks for a paper ordered by total marks descending — the input ordering for
     * leaderboard rank generation.
     *
     * @param paperId the paper's ID
     * @return every mark for the paper, highest total first
     */
    List<PaperMark> findByPaperIdOrderByTotalMarksDesc(UUID paperId);

    /**
     * A page of marks for a paper that currently have a leaderboard rank assigned, ordered by
     * that rank — the leaderboard read query, paginated since a paper can realistically have
     * thousands of ranked students. Marks entered after the paper's most recent "Generate
     * Ranks" run have a null rank and are naturally excluded. Secondarily ordered by ID so tied
     * ranks (which share the same rank number) come back in a stable order across repeated
     * reads, rather than whatever order the database happens to return them in.
     *
     * @param paperId the paper's ID
     * @param pageable pagination information
     * @return a page of the current leaderboard entries, best rank first, plus the total count
     */
    Page<PaperMark> findByPaperIdAndRankIsNotNullOrderByRankAscIdAsc(UUID paperId, Pageable pageable);

    /**
     * The calling student's own ranked mark on a paper, regardless of where it falls in the
     * leaderboard's page ordering — lets the leaderboard surface "your rank" without the caller
     * needing to page through potentially thousands of entries to find themselves.
     *
     * @param paperId the paper's ID
     * @param studentId the caller's ID
     * @return the caller's ranked mark, if they have one
     */
    Optional<PaperMark> findByPaperIdAndStudentIdAndRankIsNotNull(UUID paperId, UUID studentId);

}
