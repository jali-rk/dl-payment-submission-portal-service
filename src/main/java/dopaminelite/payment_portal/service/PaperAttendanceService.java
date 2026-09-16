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
import java.util.HashMap;
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
     * in the slot data that didn't resolve to an active center at all - which covers genuinely
     * unknown/soft-deleted centers. Rows with no center recorded at all are grouped under
     * "Not specified".
     *
     * <p>Both dl-user-service (where {@code users.paper_center} is meant to hold a name but has
     * been observed holding the center's raw UUID for some students) and this service's own
     * snapshot (where {@code student_paper_center_id} is meant to hold a UUID but has been
     * observed holding the center's name for others - see {@code
     * PaymentSubmissionRepository.findByAdminFilters}'s Javadoc for the same issue elsewhere)
     * have this id/name mix-up, in either direction, depending on when a given row was written.
     * Grouping by the raw key alone would therefore split one physical center's true totals
     * across two identically-named rows - one reached via its id, the other via its name. Every
     * raw key is resolved to a canonical center id first (via {@link #canonicalCenterKey}) and
     * merged before any row is built, so both variants always land in the same row.
     *
     * @param paperId the paper to summarize
     * @return the per-center rows plus a grand-totals row
     * @throws ResourceNotFoundException if no paper exists with the given ID
     */
    public PaperCenterAttendanceSummaryResponse getByCenterSummary(UUID paperId) {
        Paper paper = requirePaper(paperId);

        List<PaperSlotRepository.CenterAttendanceAggregate> aggregates =
                paperSlotRepository.aggregateAttendanceByCenter(paperId, PaperWritingMode.PHYSICAL);

        // Active centers decide which zero-slot placeholder rows appear; the full (active +
        // deleted) map is only for resolving/merging raw keys, so a since-deleted center with
        // real historical data still gets its actual name instead of an empty row of its own.
        Map<String, String> activeCenterNames = paperCenterService.getActivePaperCenterNameMap();
        Map<String, String> allCenterNames = paperCenterService.getAllPaperCenterNameMap();
        Map<String, String> centerIdsByName = invertCenterNames(allCenterNames);

        Map<String, long[]> countsByCanonicalKey = new LinkedHashMap<>();
        for (PaperSlotRepository.CenterAttendanceAggregate aggregate : aggregates) {
            String canonicalKey = canonicalCenterKey(aggregate.getCenterId(), allCenterNames, centerIdsByName);
            long[] counts = countsByCanonicalKey.computeIfAbsent(canonicalKey, k -> new long[2]);
            counts[0] += aggregate.getOpened();
            counts[1] += aggregate.getAttended();
        }

        List<PaperCenterAttendanceRowDto> rows = new ArrayList<>();
        activeCenterNames.forEach((centerId, centerName) ->
                rows.add(toRow(centerId, centerName, countsByCanonicalKey.remove(centerId))));

        // Anything left is either a soft-deleted center with real historical data (resolve its
        // actual name via allCenterNames), a genuinely unknown key, or "no center recorded"
        // (surfaced as UNASSIGNED_CENTER_KEY, a real filterable value, not null) - every row
        // that matched an active center by either id or name was already merged above.
        countsByCanonicalKey.forEach((centerKey, counts) -> {
            boolean isUnassigned = UNASSIGNED_CENTER_KEY.equals(centerKey);
            String displayName = isUnassigned ? NOT_SPECIFIED_LABEL : allCenterNames.getOrDefault(centerKey, centerKey);
            rows.add(toRow(centerKey, displayName, counts));
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
     * <p>When {@code centerId} is a real active center's id, matching also needs to catch rows
     * that were snapshotted with that center's <em>name</em> instead (see {@link
     * #getByCenterSummary}'s Javadoc) - the resolved name is looked up here and passed to the
     * repository as an alternate value to match, so a row like a summary row's student count
     * never appears to be missing students just because of which format that row happened to
     * be written in.
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

        Map<String, String> allCenterNames = paperCenterService.getAllPaperCenterNameMap();
        Map<String, String> centerIdsByName = invertCenterNames(allCenterNames);
        String centerIdAlternate = centerId != null ? allCenterNames.get(centerId) : null;

        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<PaperSlot> page = paperSlotRepository.findAttendanceStudents(
                paperId, centerId, centerIdAlternate, attended, pageable);

        List<PaperCenterAttendanceStudentDto> items = page.getContent().stream()
                .map(slot -> toStudentDto(slot, allCenterNames, centerIdsByName))
                .toList();

        return new PaginatedResponse<>(items, page.getTotalElements());
    }

    private Paper requirePaper(UUID paperId) {
        return paperRepository.findById(paperId)
                .orElseThrow(() -> new ResourceNotFoundException("Paper not found with id: " + paperId));
    }

    private Map<String, String> invertCenterNames(Map<String, String> centerNames) {
        Map<String, String> centerIdsByName = new HashMap<>();
        centerNames.forEach((id, name) -> centerIdsByName.put(name, id));
        return centerIdsByName;
    }

    /**
     * Resolves a raw {@code student_paper_center_id} value to a canonical center id: itself if
     * it's already a real active center's id; that center's id if it's actually the center's
     * name (the known id/name mix-up - see {@link #getByCenterSummary}); {@link
     * #UNASSIGNED_CENTER_KEY} if blank/null; otherwise the raw value itself, as its own
     * unresolvable bucket (an unknown or soft-deleted center).
     */
    private String canonicalCenterKey(String rawKey, Map<String, String> centerNames, Map<String, String> centerIdsByName) {
        if (rawKey == null || rawKey.isBlank()) {
            return UNASSIGNED_CENTER_KEY;
        }
        if (centerNames.containsKey(rawKey)) {
            return rawKey;
        }
        return centerIdsByName.getOrDefault(rawKey, rawKey);
    }

    private PaperCenterAttendanceRowDto toRow(String centerId, String centerName, long[] counts) {
        long opened = counts == null ? 0 : counts[0];
        long attended = counts == null ? 0 : counts[1];
        long absent = opened - attended;
        double rate = opened == 0 ? 0.0 : Math.round(attended * 1000.0 / opened) / 10.0;
        return new PaperCenterAttendanceRowDto(centerId, centerName, opened, attended, absent, rate);
    }

    private PaperCenterAttendanceStudentDto toStudentDto(
            PaperSlot slot, Map<String, String> centerNames, Map<String, String> centerIdsByName) {
        StudentSnapshot snapshot = slot.getPaymentSubmission().getStudentSnapshot();
        String canonicalId = canonicalCenterKey(snapshot.getPaperCenterId(), centerNames, centerIdsByName);
        boolean isUnassigned = UNASSIGNED_CENTER_KEY.equals(canonicalId);

        return new PaperCenterAttendanceStudentDto(
                slot.getPaymentSubmission().getStudentId(),
                snapshot.getCodeNumber(),
                snapshot.getFullName(),
                canonicalId,
                isUnassigned ? NOT_SPECIFIED_LABEL : centerNames.getOrDefault(canonicalId, canonicalId),
                slot.getConsumedAt() != null,
                slot.getConsumedAt()
        );
    }

}
