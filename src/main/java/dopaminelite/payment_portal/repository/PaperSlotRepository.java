package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaperSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PaperSlot entity operations.
 */
@Repository
public interface PaperSlotRepository extends JpaRepository<PaperSlot, UUID> {

    Optional<PaperSlot> findByQrToken(UUID qrToken);

    boolean existsByPaperIdAndPaymentSubmissionId(UUID paperId, UUID paymentSubmissionId);

    boolean existsByPaperId(UUID paperId);

    Optional<PaperSlot> findByPaperIdAndPaymentSubmissionId(UUID paperId, UUID paymentSubmissionId);

    /**
     * Atomically marks a slot as consumed, but only if it is currently unconsumed AND the
     * owning paper's validity window currently includes {@code today}. This is a single
     * conditional UPDATE (not read-then-write) so concurrent double-scans of the same slot
     * can never both succeed.
     *
     * @return the number of rows updated: 1 on success, 0 if already consumed or out of window
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE paper_slots
            SET consumed_at = :now, consumed_by_instructor_id = :instructorId
            FROM papers
            WHERE paper_slots.id = :id
              AND paper_slots.paper_id = papers.id
              AND paper_slots.consumed_at IS NULL
              AND :today BETWEEN papers.start_date AND papers.end_date
            """, nativeQuery = true)
    int consumeIfAvailable(
            @Param("id") UUID id,
            @Param("now") LocalDateTime now,
            @Param("instructorId") UUID instructorId,
            @Param("today") LocalDate today
    );

    /**
     * Finds slots matching optional filters, with the computed status filter expressed
     * directly in JPQL (against the joined paper's date window and consumedAt) so pagination
     * stays correct.
     *
     * @param paperId filter by paper, null for no filtering
     * @param studentId filter by the submitting student's ID, null for no filtering
     * @param studentCodeNumber filter by the submitting student's code number, null for no filtering
     * @param statusFilter one of SCHEDULED/AVAILABLE/EXPIRED/CONSUMED (as its enum name), null for no filtering
     * @param today the current date
     * @param pageable pagination information
     * @return a page of matching slots, with paper/submission/portal eagerly fetched
     */
    @Query("SELECT ps FROM PaperSlot ps " +
           "JOIN FETCH ps.paper p " +
           "JOIN FETCH ps.paymentSubmission sub " +
           "JOIN FETCH sub.portal " +
           "WHERE (:paperId IS NULL OR p.id = :paperId) AND " +
           "(:studentId IS NULL OR sub.studentId = :studentId) AND " +
           "(:studentCodeNumber IS NULL OR sub.studentSnapshot.codeNumber = :studentCodeNumber) AND " +
           "(:statusFilter IS NULL OR " +
           "  (CAST(:statusFilter AS string) = 'CONSUMED' AND ps.consumedAt IS NOT NULL) OR " +
           "  (CAST(:statusFilter AS string) != 'CONSUMED' AND ps.consumedAt IS NULL AND " +
           "    ((CAST(:statusFilter AS string) = 'SCHEDULED' AND :today < p.startDate) OR " +
           "     (CAST(:statusFilter AS string) = 'AVAILABLE' AND :today BETWEEN p.startDate AND p.endDate) OR " +
           "     (CAST(:statusFilter AS string) = 'EXPIRED' AND :today > p.endDate)))) " +
           "ORDER BY ps.createdAt DESC")
    Page<PaperSlot> findByFilters(
            @Param("paperId") UUID paperId,
            @Param("studentId") UUID studentId,
            @Param("studentCodeNumber") String studentCodeNumber,
            @Param("statusFilter") String statusFilter,
            @Param("today") LocalDate today,
            Pageable pageable
    );

}
