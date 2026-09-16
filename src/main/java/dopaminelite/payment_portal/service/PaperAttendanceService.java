package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.dto.common.PaginatedResponse;
import dopaminelite.payment_portal.dto.paper.PaperCenterAttendanceRowDto;
import dopaminelite.payment_portal.dto.paper.PaperCenterAttendanceStudentDto;
import dopaminelite.payment_portal.dto.paper.PaperCenterAttendanceSummaryResponse;
import dopaminelite.payment_portal.entity.Paper;
import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.StudentSnapshot;
import dopaminelite.payment_portal.entity.enums.PaperWritingMode;
import dopaminelite.payment_portal.exception.ResourceNotFoundException;
import dopaminelite.payment_portal.repository.PaperRepository;
import dopaminelite.payment_portal.repository.PaperSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates a paper's {@link PaperSlot} attendance by the physical paper center its students
 * declared at payment time - backs the admin/instructor "Paper Center Attendance" dashboard.
 *
 * <p>Center attribution is inherently self-declared (the student's choice at payment submission
 * time), not verified against where they were actually scanned - see
 * {@code StudentSnapshot.paperCenterId}'s Javadoc. This service only aggregates what the data
 * already records.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaperAttendanceService {

    private static final String NOT_SPECIFIED_LABEL = "Not specified";
    private static final String TOTALS_LABEL = "All centers";

    /**
     * Sentinel {@code paperCenterId}/{@code centerId} value standing in for "no center
     * recorded" - a real value rather than {@code null} so it round-trips through query
     * params (an omitted {@code centerId} means something different: no center filter at all,
     * i.e. every student regardless of center - see {@link #getStudents}).
     */
    public static final String UNASSIGNED_CENTER_KEY = "UNASSIGNED";

    private final PaperRepository paperRepository;
    private final PaperSlotRepository paperSlotRepository;
    private final PaperCenterService paperCenterService;

    /**
     * Builds the per-center attendance breakdown for a paper: every currently-active paper
     * center appears as a row (even with zero slots for this paper), plus any center key found
     * in the slot data that didn't resolve to an active center - which covers both genuinely
     * unknown/soft-deleted centers and the known legacy bug where some rows hold a center name
     * in place of its ID (see {@code PaymentSubmissionRepository.findByAdminFilters}'s Javadoc
     * for the same workaround elsewhere). Rows with no center recorded at all are grouped under
     * "Not specified".
     *
     * @param paperId the paper to summarize
     * @return the per-center rows plus a grand-totals row
     * @throws ResourceNotFoundException if no paper exists with the given ID
     */
    public PaperCenterAttendanceSummaryResponse getByCenterSummary(UUID paperId) {
        Paper paper = requirePaper(paperId);

        List<PaperSlotRepository.CenterAttendanceAggregate> aggregates =
                paperSlotRepository.aggregateAttendanceByCenter(paperId, PaperWritingMode.PHYSICAL);

        Map<String, long[]> countsByCenterKey = new LinkedHashMap<>();
        for (PaperSlotRepository.CenterAttendanceAggregate aggregate : aggregates) {
            countsByCenterKey.put(aggregate.getCenterId(),
                    new long[]{aggregate.getOpened(), aggregate.getAttended()});
        }

        Map<String, String> centerNames = paperCenterService.getPaperCenterNameMap();

        List<PaperCenterAttendanceRowDto> rows = new ArrayList<>();
        centerNames.forEach((centerId, centerName) ->
                rows.add(toRow(centerId, centerName, countsByCenterKey.remove(centerId))));

        // Anything left didn't match an active center: a legacy name-in-ID-column row, a
        // soft-deleted center, or students with no center recorded (null key) at all - the
        // latter is surfaced as UNASSIGNED_CENTER_KEY, a real filterable value, not null.
        countsByCenterKey.forEach((centerKey, counts) -> {
            boolean isUnassigned = centerKey == null || centerKey.isBlank();
            String resolvedId = isUnassigned ? UNASSIGNED_CENTER_KEY : centerKey;
            String displayName = isUnassigned ? NOT_SPECIFIED_LABEL : centerKey;
            rows.add(toRow(resolvedId, displayName, counts));
        });

        rows.sort(Comparator.comparing(PaperCenterAttendanceRowDto::getPaperCenterName, String.CASE_INSENSITIVE_ORDER));

        long totalOpened = rows.stream().mapToLong(PaperCenterAttendanceRowDto::getOpened).sum();
        long totalAttended = rows.stream().mapToLong(PaperCenterAttendanceRowDto::getAttended).sum();
        PaperCenterAttendanceRowDto totals = toRow(null, TOTALS_LABEL, new long[]{totalOpened, totalAttended});

        return new PaperCenterAttendanceSummaryResponse(paper.getId(), paper.getTitle(), totals, rows);
    }

    /**
     * Lists the students who have a slot for a paper, filterable by center and/or attendance -
     * backs the dashboard's "Students" tab. Passing a summary row's {@code paperCenterId}
     * straight back as {@code centerId} (including {@link #UNASSIGNED_CENTER_KEY}) returns
     * exactly that row's students; omitting {@code centerId} returns every student regardless
     * of center.
     *
     * @param paperId the paper to list attendance for
     * @param centerId null for no center filter, {@link #UNASSIGNED_CENTER_KEY} for students
     *        with no center recorded, else an exact raw center key
     * @param attended null for no attendance filter, true/false to match attended/absent only
     * @param limit maximum number of results per page
     * @param offset number of results to skip
     * @return paginated student rows, alphabetical by name
     * @throws ResourceNotFoundException if no paper exists with the given ID
     */
    public PaginatedResponse<PaperCenterAttendanceStudentDto> getStudents(
            UUID paperId, String centerId, Boolean attended, int limit, int offset) {
        requirePaper(paperId);

        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<PaperSlot> page = paperSlotRepository.findAttendanceStudents(paperId, centerId, attended, pageable);

        Map<String, String> centerNames = paperCenterService.getPaperCenterNameMap();
        List<PaperCenterAttendanceStudentDto> items = page.getContent().stream()
                .map(slot -> toStudentDto(slot, centerNames))
                .toList();

        return new PaginatedResponse<>(items, page.getTotalElements());
    }

    private Paper requirePaper(UUID paperId) {
        return paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));
    }

    private PaperCenterAttendanceRowDto toRow(String centerId, String centerName, long[] counts) {
        long opened = counts == null ? 0 : counts[0];
        long attended = counts == null ? 0 : counts[1];
        long absent = opened - attended;
        double rate = opened == 0 ? 0.0 : Math.round(attended * 1000.0 / opened) / 10.0;
        return new PaperCenterAttendanceRowDto(centerId, centerName, opened, attended, absent, rate);
    }

    private PaperCenterAttendanceStudentDto toStudentDto(PaperSlot slot, Map<String, String> centerNames) {
        StudentSnapshot snapshot = slot.getPaymentSubmission().getStudentSnapshot();
        String rawCenterId = snapshot.getPaperCenterId();
        boolean isUnassigned = rawCenterId == null || rawCenterId.isBlank();

        return new PaperCenterAttendanceStudentDto(
                slot.getPaymentSubmission().getStudentId(),
                snapshot.getCodeNumber(),
                snapshot.getFullName(),
                isUnassigned ? UNASSIGNED_CENTER_KEY : rawCenterId,
                isUnassigned ? NOT_SPECIFIED_LABEL : centerNames.getOrDefault(rawCenterId, rawCenterId),
                slot.getConsumedAt() != null,
                slot.getConsumedAt()
        );
    }

}
