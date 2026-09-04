package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.CalendarEventAttendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CalendarEventAttendeeRepository extends JpaRepository<CalendarEventAttendee, UUID> {

    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    Optional<CalendarEventAttendee> findByEventIdAndUserId(UUID eventId, UUID userId);

    List<CalendarEventAttendee> findByEventId(UUID eventId);

    /**
     * Just the user IDs already attached to an event — one query per event instead of one
     * {@code EXISTS} check per (event, candidate student) pair, for bulk-attach paths like
     * {@code CalendarEventService.syncClassEnrollmentToEvents} where a class enrollment sync
     * can realistically involve hundreds of students.
     */
    @Query("SELECT a.userId FROM CalendarEventAttendee a WHERE a.event.id = :eventId")
    List<UUID> findUserIdsByEventId(@Param("eventId") UUID eventId);

    /**
     * Admin-added attendees only — excludes system-attached students (whose
     * {@code addedByUserId} is null). Backs the Papers admin "Manage Attendees" picker, which
     * must never list or remove a student's system-granted visibility.
     */
    List<CalendarEventAttendee> findByEventIdAndAddedByUserIdIsNotNull(UUID eventId);

    /**
     * Scoped to admin-added rows only (see {@link #findByEventIdAndAddedByUserIdIsNotNull}) —
     * a no-op if {@code userId} only has a system-attached row, so this can never be used to
     * revoke a student's earned calendar visibility.
     */
    @Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM CalendarEventAttendee a WHERE a.event.id = :eventId AND a.userId = :userId AND a.addedByUserId IS NOT NULL")
    void deleteAdminAddedByEventIdAndUserId(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

}
