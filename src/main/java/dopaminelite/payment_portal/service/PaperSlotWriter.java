package dopaminelite.payment_portal.service;

import dopaminelite.payment_portal.entity.PaperSlot;
import dopaminelite.payment_portal.entity.PaperSlotCreationResult;
import dopaminelite.payment_portal.entity.enums.PaperSlotCreationOutcome;
import dopaminelite.payment_portal.repository.PaperRepository;
import dopaminelite.payment_portal.repository.PaperSlotCreationResultRepository;
import dopaminelite.payment_portal.repository.PaperSlotRepository;
import dopaminelite.payment_portal.repository.PaymentSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Low-level, per-attempt persistence primitives for paper slot creation. Each method here is
 * its own {@code REQUIRES_NEW} transaction, deliberately kept small and single-purpose.
 *
 * <p>This isolation matters beyond "one paper's failure can't affect another's": once a
 * statement fails inside a Postgres transaction (e.g. a unique constraint violation on
 * insert), that transaction is aborted at the database level and every subsequent statement
 * on it fails too, even unrelated reads — Postgres won't run anything else on an aborted
 * transaction. So catching an exception and continuing to do more work in the *same*
 * transaction is not safe here. Instead, each of these methods lets its own exception
 * propagate to unwind cleanly via Spring's default rollback behavior, and the caller
 * ({@link PaperSlotService}) reacts to that failure by starting a fresh call — a new,
 * unaffected transaction — rather than trying to recover inline.
 */
@Service
@RequiredArgsConstructor
public class PaperSlotWriter {

    private final PaperSlotRepository paperSlotRepository;
    private final PaperSlotCreationResultRepository resultRepository;
    private final PaperRepository paperRepository;
    private final PaymentSubmissionRepository paymentSubmissionRepository;

    /**
     * Attempts to insert a new slot for the given (paper, submission) pair.
     *
     * @return the newly created slot, or null if a pre-check found one already exists
     * @throws org.springframework.dao.DataIntegrityViolationException if a concurrent attempt
     *         won the race between the pre-check and this insert (caller must not retry in
     *         the same transaction — this one is now aborted and must be allowed to roll back)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaperSlot insertIfAbsent(UUID submissionId, UUID paperId) {
        if (paperSlotRepository.existsByPaperIdAndPaymentSubmissionId(paperId, submissionId)) {
            return null;
        }

        PaperSlot slot = new PaperSlot();
        slot.setPaper(paperRepository.getReferenceById(paperId));
        slot.setPaymentSubmission(paymentSubmissionRepository.getReferenceById(submissionId));
        return paperSlotRepository.saveAndFlush(slot);
    }

    /**
     * Fetches the existing slot for a (paper, submission) pair, in a fresh transaction —
     * used after a race or a pre-existing-slot signal, never inside the transaction that
     * detected either.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PaperSlot findExisting(UUID paperId, UUID submissionId) {
        return paperSlotRepository.findByPaperIdAndPaymentSubmissionId(paperId, submissionId).orElse(null);
    }

    /**
     * Upserts the single current {@code PaperSlotCreationResult} row for a (submission, paper)
     * pair, always in its own fresh transaction so it succeeds independently of whatever
     * happened (or failed) in the slot-insert attempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaperSlotCreationResult upsertResult(
            UUID submissionId,
            UUID paperId,
            String paperTitle,
            PaperSlotCreationOutcome outcome,
            String message,
            PaperSlot slot
    ) {
        PaperSlotCreationResult result = resultRepository
                .findByPaymentSubmissionIdAndPaperId(submissionId, paperId)
                .orElseGet(() -> {
                    PaperSlotCreationResult fresh = new PaperSlotCreationResult();
                    fresh.setPaymentSubmission(paymentSubmissionRepository.getReferenceById(submissionId));
                    fresh.setPaper(paperRepository.getReferenceById(paperId));
                    return fresh;
                });

        result.setPaperTitleSnapshot(paperTitle);
        result.setPaperSlot(slot);
        result.setOutcome(outcome);
        result.setMessage(message);
        result.setAttemptedAt(LocalDateTime.now(ZoneId.of("Asia/Colombo")));

        return resultRepository.save(result);
    }

}
