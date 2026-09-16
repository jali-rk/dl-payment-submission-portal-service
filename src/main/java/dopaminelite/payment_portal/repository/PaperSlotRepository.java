package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.enums.PaperWritingMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * Aggregates a paper's slots by the submitting student's declared paper center, counting
     * how many slots exist ("opened") and how many are consumed ("attended") per center. Only
     * {@code PHYSICAL}-mode students are included — {@code ONLINE}/{@code NOT_WRITING} students
     * have no meaningful center. The grouping key is the raw, unresolved
     * {@code student_paper_center_id} string (including {@code null} for students missing one);
     * resolving it to a display name — and handling the known legacy bug where some rows hold a
     * center *name* in that column instead of a UUID — is the caller's responsibility.
     *
     * @param paperId the paper to aggregate attendance for
     * @param paperWritingMode always {@link PaperWritingMode#PHYSICAL}, passed as a parameter
     *        rather than hardcoded in JPQL so the enum comparison is type-checked
     * @return one row per distinct center key found among this paper's slots
     */
    @Query("SELECT ps.paymentSubmission.studentSnapshot.paperCenterId AS centerId, " +
           "COUNT(ps) AS opened, " +
           "SUM(CASE WHEN ps.consumedAt IS NOT NULL THEN 1 ELSE 0 END) AS attended " +
           "FROM PaperSlot ps " +
           "WHERE ps.paper.id = :paperId " +
           "AND ps.paymentSubmission.studentSnapshot.paperWritingMode = :paperWritingMode " +
           "GROUP BY ps.paymentSubmission.studentSnapshot.paperCenterId")
    List<CenterAttendanceAggregate> aggregateAttendanceByCenter(
            @Param("paperId") UUID paperId,
            @Param("paperWritingMode") PaperWritingMode paperWritingMode
    );

    /**
     * Finds slots for a paper, optionally filtered by the submitting student's declared center
     * and/or whether the slot has been consumed - backs the attendance dashboard's filterable,
     * exportable student list. Always restricted to {@code PHYSICAL}-mode students, same as
     * {@link #aggregateAttendanceByCenter} - an ONLINE/NOT_WRITING student has no relationship
     * to a physical center at all, so including them here (even under "no center filter") would
     * both mismatch the by-center totals and wrongly conflate "writes online" with "center not
     * recorded" under the same UNASSIGNED bucket.
     *
     * <p>{@code centerId} has three states, matched against the raw, unresolved
     * {@code student_paper_center_id} string (see {@link #aggregateAttendanceByCenter}):
     * {@code null} (omitted) means no center filtering at all (every PHYSICAL student, any or no
     * center); the literal sentinel {@code "UNASSIGNED"} means students with no center recorded;
     * any other value is matched exactly against that raw key.
     *
     * @param paperId the paper to list attendance for
     * @param centerId null for no center filter, {@code "UNASSIGNED"} for no-center students, else an exact raw center key
     * @param attended null for no attendance filter, true/false to match consumed/unconsumed slots
     * @param pageable pagination information
     * @return a page of matching slots, with the submission eagerly fetched
     */
    @Query("SELECT ps FROM PaperSlot ps " +
           "JOIN FETCH ps.paymentSubmission sub " +
           "WHERE ps.paper.id = :paperId AND " +
           "sub.studentSnapshot.paperWritingMode = dopaminelite.payment_portal.entity.enums.PaperWritingMode.PHYSICAL AND " +
           "(:centerId IS NULL OR " +
           "  (:centerId = 'UNASSIGNED' AND sub.studentSnapshot.paperCenterId IS NULL) OR " +
           "  (:centerId <> 'UNASSIGNED' AND sub.studentSnapshot.paperCenterId = :centerId)) AND " +
           "(:attended IS NULL OR " +
           "  (:attended = TRUE AND ps.consumedAt IS NOT NULL) OR " +
           "  (:attended = FALSE AND ps.consumedAt IS NULL)) " +
           "ORDER BY sub.studentSnapshot.fullName ASC")
    Page<PaperSlot> findAttendanceStudents(
            @Param("paperId") UUID paperId,
            @Param("centerId") String centerId,
            @Param("attended") Boolean attended,
            Pageable pageable
    );

    /**
     * Projection for {@link #aggregateAttendanceByCenter}'s grouped counts.
     */
    interface CenterAttendanceAggregate {
        String getCenterId();
        Long getOpened();
        Long getAttended();
    }

}
