package dopaminelite.payment_portal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

}
