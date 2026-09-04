package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.CalendarEvent;
import dopaminelite.payment_portal.entity.enums.CalendarSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link CalendarEvent}. The three range-scoped finders below back
 * {@code CalendarEventService.listForUser}'s visibility union — see that method's Javadoc.
 */
@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    Optional<CalendarEvent> findBySourcePaperId(UUID paperId);

    boolean existsBySourcePaperId(UUID paperId);

    @Query("SELECT e FROM CalendarEvent e WHERE e.sourceType = :sourceType AND e.ownerId = :ownerId "
            + "AND e.startAt <= :rangeEnd AND e.endAt >= :rangeStart")
    List<CalendarEvent> findOwnedInRange(
            @Param("sourceType") CalendarSourceType sourceType,
            @Param("ownerId") UUID ownerId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    @Query("SELECT DISTINCT e FROM CalendarEvent e JOIN CalendarEventAttendee a ON a.event.id = e.id "
            + "WHERE a.userId = :userId AND e.startAt <= :rangeEnd AND e.endAt >= :rangeStart")
    List<CalendarEvent> findAttendingInRange(
            @Param("userId") UUID userId,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    @Query("SELECT e FROM CalendarEvent e WHERE e.sourceType = :sourceType "
            + "AND e.startAt <= :rangeEnd AND e.endAt >= :rangeStart")
    List<CalendarEvent> findBySourceTypeInRange(
            @Param("sourceType") CalendarSourceType sourceType,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    /**
     * Every {@code CLASS} event marked global — the blanket "every student sees it" rule,
     * computed live with no stored attendee rows (same spirit as instructor/PAPER visibility).
     */
    @Query("SELECT e FROM CalendarEvent e WHERE e.sourceType = dopaminelite.payment_portal.entity.enums.CalendarSourceType.CLASS "
            + "AND e.global = true AND e.startAt <= :rangeEnd AND e.endAt >= :rangeStart")
    List<CalendarEvent> findGlobalClassEventsInRange(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

}
