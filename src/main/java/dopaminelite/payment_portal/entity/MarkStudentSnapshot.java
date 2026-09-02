package dopaminelite.payment_portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * Snapshot of a student's identity, captured on a {@link PaperMark} at the moment it was
 * entered, from the BFF's student-by-code-number lookup. Deliberately not the same embeddable
 * as {@link StudentSnapshot} — that one carries ~12 mostly-required fields (address, NIC,
 * school, ...) sourced from a payment submission, none of which the student-lookup response
 * provides.
 */
@Getter
@Setter
@Embeddable
public class MarkStudentSnapshot {

    @Column(name = "student_code_number", length = 50)
    private String codeNumber;

    @Column(name = "student_full_name", length = 255, nullable = false)
    private String fullName;

    @Column(name = "student_email", length = 255, nullable = false)
    private String email;

    @Column(name = "student_whatsapp_number", length = 20, nullable = false)
    private String whatsappNumber;

}
