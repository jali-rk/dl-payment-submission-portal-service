package dopaminelite.payment_portal.mapper;

import dopaminelite.payment_portal.dto.calendar.CalendarEventAttendeeDto;
import dopaminelite.payment_portal.dto.calendar.CalendarEventResponse;
import dopaminelite.payment_portal.dto.calendar.ClassRefDto;
import dopaminelite.payment_portal.entity.CalendarEvent;
import dopaminelite.payment_portal.entity.CalendarEventAttendee;
import dopaminelite.payment_portal.entity.CalendarEventClass;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.enums.CalendarSourceType;
import dopaminelite.payment_portal.repository.CalendarEventClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CalendarEventMapper {

    private final CalendarEventClassRepository calendarEventClassRepository;

    public CalendarEventResponse toResponse(CalendarEvent event) {
        if (event == null) {
            return null;
        }

        CalendarEventResponse response = new CalendarEventResponse();
        response.setId(event.getId());
        response.setOwnerId(event.getOwnerId());
        response.setTitle(event.getTitle());
        response.setDescription(event.getDescription());
        response.setStartAt(event.getStartAt());
        response.setEndAt(event.getEndAt());
        response.setAllDay(event.isAllDay());
        response.setColor(event.getColor());
        response.setSourceType(event.getSourceType());
        response.setCreatedAt(event.getCreatedAt());
        response.setUpdatedAt(event.getUpdatedAt());

        if (event.getSourceType() == CalendarSourceType.PAPER && event.getSourcePaper() != null) {
            Paper paper = event.getSourcePaper();
            response.setPaperId(paper.getId());
            response.setPaperTitle(paper.getTitle());
            List<String> portalNames = paper.getLinkedPortals().stream()
                    .map(portal -> portal.getDisplayName())
                    .collect(Collectors.toList());
            response.setLinkedPortalNames(portalNames);
        }

        if (event.getSourceType() == CalendarSourceType.CLASS) {
            response.setGlobal(event.isGlobal());
            if (!event.isGlobal()) {
                List<ClassRefDto> classes = calendarEventClassRepository.findByEventId(event.getId()).stream()
                        .map(this::toClassRef)
                        .collect(Collectors.toList());
                response.setClasses(classes);
            }
        }

        return response;
    }

    private ClassRefDto toClassRef(CalendarEventClass link) {
        return new ClassRefDto(link.getClassId(), link.getClassName());
    }

    public CalendarEventAttendeeDto toAttendeeDto(CalendarEventAttendee attendee) {
        if (attendee == null) {
            return null;
        }

        CalendarEventAttendeeDto dto = new CalendarEventAttendeeDto();
        dto.setId(attendee.getId());
        dto.setUserId(attendee.getUserId());
        dto.setEmail(attendee.getEmail());
        dto.setFullName(attendee.getFullName());
        dto.setRole(attendee.getRole());
        dto.setAddedByUserId(attendee.getAddedByUserId());
        dto.setAddedAt(attendee.getCreatedAt());
        return dto;
    }

}
