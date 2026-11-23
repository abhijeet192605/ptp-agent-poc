package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.CallSession;
import com.bytewise.ptp.domain.PromiseGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The actions the agent may take. One instance per call turn, bound to that call's session.
 * Every action is checked in Java before it has any effect; the return string tells the model what happened.
 */
public class PtpTools {

    private static final Logger log = LoggerFactory.getLogger(PtpTools.class);

    private final CallSession session;
    private final PromiseGate gate;
    private final PromiseStore store;
    private final LocalDate today;
    private int toolCalls;
    private boolean closed;

    /** Whether the model called any tool this turn. */
    public boolean wasUsed() {
        return toolCalls > 0;
    }

    /**
     * Ends the turn: a model call that was abandoned after a timeout can no longer change anything.
     * Synchronized with the tools, so a tool that is already running finishes first and the turn then sees its effect.
     */
    public synchronized void close() {
        closed = true;
    }

    public PtpTools(CallSession session, PromiseGate gate, PromiseStore store, LocalDate today) {
        this.session = session;
        this.gate = gate;
        this.store = store;
        this.today = today;
    }

    @Tool(description = "Save the customer's promise to pay. Call ONLY after the customer has stated both an "
            + "amount and a payment date in their own words. If the customer splits the payment into several "
            + "installments, call this once: the system reads every installment from their words. "
            + "Returns SAVED, or REJECTED with the reasons.")
    public synchronized String recordPromise(
            @ToolParam(description = "Amount in rupees, as a number. Omit it when the customer names a share of "
                    + "the outstanding balance (full, half, 50 percent): the system calculates that itself",
                    required = false) BigDecimal amount,
            @ToolParam(description = "Payment date as YYYY-MM-DD") String date,
            @ToolParam(description = "UPI, CASH, CHEQUE or BANK_TRANSFER; omit if not stated", required = false) String mode,
            @ToolParam(description = "What the payment depends on, if anything", required = false) String conditions) {
        toolCalls++;
        if (closed) {
            return "IGNORED: this turn is over";
        }
        if (!session.isOpen()) {
            return "IGNORED: the call is already " + session.status();
        }
        LocalDate proposedDate = null;
        if (date != null && !date.isBlank()) {
            try {
                proposedDate = LocalDate.parse(date.trim());
            } catch (DateTimeParseException e) {
                // the gate will reject it for a missing date unless the transcript lets it resolve one
            }
        }

        var decision = gate.check(amount, proposedDate, mode, conditions,
                session.latestCustomerMessage(), session.allCustomerText(), session.outstanding(), today);
        log.debug("[{}] recordPromise amount={} date={} -> accepted={} reasons={}",
                session.callId(), amount, date, decision.accepted(), decision.reasons());

        if (!decision.accepted()) {
            return "REJECTED: " + String.join("; ", decision.reasons())
                    + ". Do not guess. Ask the customer for what is missing.";
        }
        var plan = decision.plan();
        if (!store.save(session.callId(), plan)) {
            return "IGNORED: a promise is already saved for this call";
        }
        session.markSaved(plan);
        return "SAVED: " + plan.installments().size() + " installment(s), total Rs "
                + plan.total().stripTrailingZeros().toPlainString()
                + ". Confirm this to the customer and end the call politely.";
    }

    @Tool(description = "Hand the call to a human agent. Use when the customer is abusive, disputes the loan, "
            + "asks for a person, or still refuses to commit after being asked twice.")
    public synchronized String escalateToHuman(@ToolParam(description = "Short reason") String reason) {
        toolCalls++;
        if (closed) {
            return "IGNORED: this turn is over";
        }
        if (!session.isOpen()) {
            return "IGNORED: the call is already " + session.status();
        }
        session.escalate(reason == null || reason.isBlank() ? "unspecified" : reason);
        log.debug("[{}] escalated: {}", session.callId(), reason);
        return "ESCALATED. Tell the customer a colleague will contact them, then end the call politely.";
    }
}
