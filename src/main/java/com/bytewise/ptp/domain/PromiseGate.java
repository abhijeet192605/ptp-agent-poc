package com.bytewise.ptp.domain;

import com.bytewise.ptp.domain.InstallmentParser.Segment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The checkpoint between the agent's proposal and the record of truth. The model proposes a promise;
 * only a promise that passes every deterministic rule here can be saved. Amount (when relative to the
 * outstanding balance), date, mode, conditions and split payments are all read from the customer's own
 * words in code, whatever the model proposed.
 */
public class PromiseGate {

    static final int MAX_INSTALLMENTS = 6;

    public record Decision(boolean accepted, PromisePlan plan, List<String> reasons) {

        /** The first installment as a single promise; convenient when there is only one. */
        public PtpExtraction promise() {
            Installment i = plan.first();
            return new PtpExtraction(true, i.amount(), i.date(), i.mode(), plan.conditions());
        }
    }

    private final PtpValidator validator = new PtpValidator();

    /** Without a known outstanding balance, so "full" or "50%" cannot be resolved. */
    public Decision check(BigDecimal amount, LocalDate proposedDate, String mode, String conditions,
                          String latestMessage, String allCustomerText, LocalDate today) {
        return check(amount, proposedDate, mode, conditions, latestMessage, allCustomerText, null, today);
    }

    /**
     * @param latestMessage   the customer's most recent message (preferred source for date and amount)
     * @param allCustomerText everything the customer has said on this call
     * @param outstanding     the loan's outstanding balance, or null if the call has no account
     */
    public Decision check(BigDecimal amount, LocalDate proposedDate, String mode, String conditions,
                          String latestMessage, String allCustomerText, BigDecimal outstanding, LocalDate today) {
        var parsed = InstallmentParser.parse(latestMessage, outstanding, today);
        if (parsed.isPlan()) {
            return checkPlan(parsed, allCustomerText, outstanding, today);
        }
        return checkSingle(amount, proposedDate, mode, latestMessage, allCustomerText, outstanding, today);
    }

    private Decision checkPlan(InstallmentParser.Parsed parsed, String allCustomerText, BigDecimal outstanding,
                               LocalDate today) {
        List<String> reasons = new ArrayList<>();
        if (!parsed.partial().isEmpty()) {
            reasons.add("an installment is missing its amount or its date");
        }
        if (parsed.complete().size() > MAX_INSTALLMENTS) {
            reasons.add("more than " + MAX_INSTALLMENTS + " installments");
        }

        List<Segment> ordered = parsed.complete().stream().sorted(Comparator.comparing(Segment::date)).toList();
        List<Installment> installments = new ArrayList<>();
        int n = 1;
        for (Segment s : ordered) {
            // each part is validated against its own words, so a weekday or amount is checked against the right part
            var probe = new PtpExtraction(true, s.amount(), s.date(), s.mode(), null);
            for (String r : validator.validate(probe, s.text(), today, s.amountDerived())) {
                reasons.add("installment " + n + ": " + r);
            }
            installments.add(new Installment(s.amount(), s.date(), s.mode()));
            n++;
        }

        PromisePlan plan = new PromisePlan(installments, ConditionFilter.fromText(allCustomerText));
        if (outstanding != null && plan.total().compareTo(outstanding) > 0) {
            reasons.add("installments total " + plan.total().stripTrailingZeros().toPlainString()
                    + " which is more than the outstanding " + outstanding.stripTrailingZeros().toPlainString());
        }
        return reasons.isEmpty() ? new Decision(true, plan, List.of()) : new Decision(false, plan, List.copyOf(reasons));
    }

    private Decision checkSingle(BigDecimal amount, LocalDate proposedDate, String mode, String latestMessage,
                                 String allCustomerText, BigDecimal outstanding, LocalDate today) {
        // "50% of outstanding", "full amount": computed here, whatever number the model proposed.
        Optional<BigDecimal> relative = AmountResolver.resolve(latestMessage, outstanding)
                .or(() -> AmountResolver.resolve(allCustomerText, outstanding));
        BigDecimal finalAmount = relative.orElse(amount);
        // "50%" with no known balance: the model's "50" is a percentage, not fifty rupees.
        if (relative.isEmpty() && AmountResolver.mentionsRelativeAmount(allCustomerText)
                && !AmountResolver.hasStatedNumber(allCustomerText)) {
            finalAmount = null;
        }

        // The model's free-text conditions and its mode are not trusted: they come only from the customer's
        // words. A mode the customer never named stays empty; the model once filled in "UPI" on its own.
        String knownConditions = ConditionFilter.fromText(allCustomerText);
        String finalMode = ModeResolver.fromText(allCustomerText);
        PtpExtraction proposal = new PtpExtraction(true, finalAmount, proposedDate, finalMode, knownConditions);

        if (!PromiseGuard.enforce(proposal, allCustomerText, today).ptpMade()) {
            return decision(false, proposal, List.of("the customer has not stated any amount or date"));
        }

        // Dates the customer stated are computed in code; the model's own date is only a fallback.
        LocalDate resolved = DateResolver.resolve(latestMessage, today)
                .or(() -> DateResolver.resolve(allCustomerText, today))
                .orElse(null);
        PtpExtraction checked = resolved == null ? proposal
                : new PtpExtraction(true, finalAmount, resolved, finalMode, knownConditions);

        List<String> reasons = new ArrayList<>(validator.validate(checked, allCustomerText, today, relative.isPresent()));
        FollowUp.askFor(checked, today).ifPresent(ask -> {
            if (checked.amount() == null || checked.amount().signum() <= 0) {
                reasons.add("amount is missing");
            }
            if (checked.date() == null) {
                reasons.add("date is missing");
            }
        });
        return decision(reasons.isEmpty(), checked, List.copyOf(reasons));
    }

    private static Decision decision(boolean accepted, PtpExtraction p, List<String> reasons) {
        var plan = new PromisePlan(List.of(new Installment(p.amount(), p.date(), p.mode())), p.conditions());
        return new Decision(accepted, plan, reasons);
    }
}
