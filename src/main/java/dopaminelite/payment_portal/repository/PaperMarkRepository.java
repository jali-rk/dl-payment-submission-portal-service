package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaperMark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

}
