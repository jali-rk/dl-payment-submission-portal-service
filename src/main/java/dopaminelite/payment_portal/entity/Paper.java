package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing an exam/course "Paper" with a validity window during which
 * approved students may redeem a {@link PaperSlot}.
 *
 * <p>NOTE: this is unrelated to {@code PaperCenterDto}/{@code PaperCenterService} (a physical
 * exam-writing location fetched from the external BFF service) and unrelated to
 * {@code PaperWritingMode} (a per-student enum on {@link StudentSnapshot}). Do not conflate
 * "Paper" (this entity) with "PaperCenter".
 */
@Getter
@Setter
@Entity
@Table(name = "papers")
public class Paper extends AuditableEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Payment portals linked to this paper. An approved submission against any of these
     * portals triggers creation of a {@link PaperSlot} for the submitting student.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "paper_payment_portals",
        joinColumns = @JoinColumn(name = "paper_id"),
        inverseJoinColumns = @JoinColumn(name = "portal_id")
    )
    private List<PaymentPortal> linkedPortals = new ArrayList<>();

    /**
     * UUID of the admin user who created this paper.
     */
    @Column(nullable = true)
    private UUID createdByAdminId;

    /**
     * Maximum mark for this paper's MCQ section, or null if the paper has no MCQ section.
     * Set independently of {@link #structuredMaxMarks}/{@link #essayMaxMarks} — the three do
     * not need to relate to each other or sum to anything. Once any {@link PaperMark} exists
     * for this paper, all three become immutable (see {@code PaperService.updateMarkScheme}).
     */
    @Column(name = "mcq_max_marks", precision = 6, scale = 3)
    private BigDecimal mcqMaxMarks;

    /**
     * Maximum mark for this paper's Structured section, or null if the paper has no such
     * section. See {@link #mcqMaxMarks} for the independence/locking rules that also apply here.
     */
    @Column(name = "structured_max_marks", precision = 6, scale = 3)
    private BigDecimal structuredMaxMarks;

    /**
     * Maximum mark for this paper's Essay section, or null if the paper has no such section.
     * See {@link #mcqMaxMarks} for the independence/locking rules that also apply here.
     */
    @Column(name = "essay_max_marks", precision = 6, scale = 3)
    private BigDecimal essayMaxMarks;

}
