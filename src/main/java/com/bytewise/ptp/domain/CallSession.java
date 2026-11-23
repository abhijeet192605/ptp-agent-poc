package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * State of one collection call. The status is owned by code, never by the model's words:
 * it only changes when a tool call actually succeeds.
 */
public class CallSession {

    /** KEPT_EXISTING: the customer confirmed the promise already on record, so nothing new was saved. */
    public enum Status { IN_PROGRESS, PTP_SAVED, ESCALATED, KEPT_EXISTING }

    /** One line of the conversation. */
    public record Entry(boolean customer, String text) {
    }

    private final String callId;
    private final Object turnLock = new Object();
    private final List<Entry> entries = new ArrayList<>();
    private Status status = Status.IN_PROGRESS;
    private PromisePlan savedPromise;
    private String escalationReason;
    private int refusals;
    private Language language = Language.ENGLISH;
    private boolean languageSeen;
    private String accountId;
    private BigDecimal outstanding;
    private boolean existingPromiseShown;

    public CallSession(String callId) {
        this.callId = callId;
    }

    /** Rebuilds a session from storage. */
    public static CallSession restore(String callId, List<Entry> entries, Status status, PromisePlan savedPromise,
                                      String escalationReason, int refusals, Language language,
                                      String accountId, BigDecimal outstanding) {
        CallSession s = new CallSession(callId);
        s.entries.addAll(entries);
        s.status = status;
        s.savedPromise = savedPromise;
        s.escalationReason = escalationReason;
        s.refusals = refusals;
        s.language = language;
        s.languageSeen = true;
        s.accountId = accountId;
        s.outstanding = outstanding;
        return s;
    }

    /**
     * Links the call to a loan account. The outstanding balance is a snapshot taken now, so the figure
     * quoted to the customer cannot change halfway through the call.
     */
    public synchronized void bindAccount(LoanAccount account) {
        this.accountId = account.accountId();
        this.outstanding = account.outstanding();
    }

    public synchronized String accountId() {
        return accountId;
    }

    /** The outstanding balance at the start of the call, or null if the call has no account. */
    public synchronized BigDecimal outstanding() {
        return outstanding;
    }

    public synchronized void addCustomerMessage(String message) {
        entries.add(new Entry(true, message));
    }

    public synchronized void addAgentMessage(String message) {
        entries.add(new Entry(false, message));
    }

    /** Remembers the customer's language; a message that detects as English never overrides Hinglish/Marathi. */
    public synchronized void observeLanguage(Language detected) {
        if (!languageSeen || detected != Language.ENGLISH) {
            language = detected;
            languageSeen = true;
        }
    }

    public synchronized void markSaved(PromisePlan promise) {
        this.savedPromise = promise;
        this.status = Status.PTP_SAVED;
    }

    /** The customer confirmed an earlier promise; it is shown, but not stored again for this call. */
    public synchronized void markKeptExisting(PromisePlan existing) {
        this.savedPromise = existing;
        this.status = Status.KEPT_EXISTING;
    }

    /** Whether the agent has already put the earlier promise in front of the customer on this call. */
    public synchronized boolean existingPromiseShown() {
        return existingPromiseShown;
    }

    public synchronized void existingPromiseShown(boolean shown) {
        this.existingPromiseShown = shown;
    }

    /** @return how many refusals this call has had so far, including this one */
    public synchronized int recordRefusal() {
        return ++refusals;
    }

    public synchronized void escalate(String reason) {
        this.escalationReason = reason;
        this.status = Status.ESCALATED;
    }

    public String callId() {
        return callId;
    }

    /**
     * Held for the whole of one turn, so two requests for the same call cannot interleave. It is separate from
     * the session's own monitor on purpose: the model's tool calls run on another thread and need that monitor
     * while the turn is waiting for them.
     */
    public Object turnLock() {
        return turnLock;
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized boolean isOpen() {
        return status == Status.IN_PROGRESS;
    }

    public synchronized PromisePlan savedPromise() {
        return savedPromise;
    }

    public synchronized String escalationReason() {
        return escalationReason;
    }

    public synchronized int refusals() {
        return refusals;
    }

    public synchronized Language language() {
        return language;
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized int customerTurns() {
        return (int) entries.stream().filter(Entry::customer).count();
    }

    /** The last thing the agent said, or empty if it has not spoken yet. */
    public synchronized String latestAgentMessage() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (!entries.get(i).customer()) {
                return entries.get(i).text();
            }
        }
        return "";
    }

    public synchronized String latestCustomerMessage() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).customer()) {
                return entries.get(i).text();
            }
        }
        return "";
    }

    public synchronized String allCustomerText() {
        return String.join(" ", entries.stream().filter(Entry::customer).map(Entry::text).toList());
    }

    /** The conversation before the latest customer message, for the prompt. */
    public synchronized String historyBeforeLatest() {
        if (entries.size() <= 1) {
            return "(none yet)";
        }
        return String.join("\n", entries.subList(0, entries.size() - 1).stream()
                .map(e -> (e.customer() ? "Customer: " : "Agent: ") + e.text()).toList());
    }
}
