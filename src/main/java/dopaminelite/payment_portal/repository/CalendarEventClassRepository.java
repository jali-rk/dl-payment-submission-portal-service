package dopaminelite.payment_portal.repository;

import dopaminelite.payment_portal.entity.CalendarEvent;
import dopaminelite.payment_portal.entity.CalendarEventClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CalendarEventClassRepository extends JpaRepository<CalendarEventClass, UUID> {

    List<CalendarEventClass> findByEventId(UUID eventId);

    /**
     * Every event linked to a class — the enrollment-sync hook uses this to find which events
     * need a newly-enrolled student attached.
     */
    @Query("SELECT DISTINCT c.event FROM CalendarEventClass c WHERE c.classId = :classId")
    List<CalendarEvent> findEventsByClassId(@Param("classId") String classId);

}
