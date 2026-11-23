package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Yes, but I will give the remaining amount next week": the customer keeps the promise already on record and
 * adds a payment for whatever is left of the outstanding balance. The new plan is the installments still due plus
 * one more, worked out in code from the balance and the customer's own words.
 */
public final class PlanExtension {

    private static final Pattern REMAINING = Pattern.compile(
            "\\b(remaining|rest|baaki|baki|bakaya|bacha|bachi|bachhi|uravlel\\w*|urlel\\w*)\\b");

    private PlanExtension() {
    }

    /** True when the customer talks about "the remaining amount" or "the rest". */
    public static boolean mentionsRemaining(String customerMessage) {
        return customerMessage != null && REMAINING.matcher(customerMessage.toLowerCase(Locale.ROOT)).find();
    }

    /**
     * @param prior       the promise on record for this account
     * @param outstanding the account's outstanding balance
     * @param message     what the customer just said
     * @return the combined plan if it passes every rule; otherwise the reasons, with {@code prior} as the plan
     */
    public static PromiseGate.Decision extend(PromisePlan prior, BigDecimal outstanding, String message,
                                              LocalDate today) {
        List<String> reasons = new ArrayList<>();

        // Installments dated before today are in the past: they are not counted as still to be paid.
        List<Installment> stillDue = prior.installments().stream()
                .filter(i -> !i.date().isBefore(today))
                .toList();
        if (stillDue.isEmpty()) {
            return rejected(prior, "no promise on record is still due");
        }
        BigDecimal committed = stillDue.stream().map(Installment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = outstanding.subtract(committed);
        if (remaining.signum() <= 0) {
            return rejected(prior, "the promise on record already covers the outstanding amount");
        }

        LocalDate date = DateResolver.resolve(message, today).orElse(null);
        if (date == null) {
            reasons.add("date is missing");
        } else if (date.isBefore(today)) {
            reasons.add("promised date " + date + " is in the past");
        } else if (date.isAfter(today.plusDays(PtpValidator.MAX_DAYS_AHEAD))) {
            reasons.add("promised date " + date + " is more than " + PtpValidator.MAX_DAYS_AHEAD + " days away");
        }
        if (stillDue.size() + 1 > PromiseGate.MAX_INSTALLMENTS) {
            reasons.add("more than " + PromiseGate.MAX_INSTALLMENTS + " installments");
        }
        if (!reasons.isEmpty()) {
            return new PromiseGate.Decision(false, prior, List.copyOf(reasons));
        }

        List<Installment> all = new ArrayList<>(stillDue);
        all.add(new Installment(remaining, date, ModeResolver.fromText(message)));
        all.sort(Comparator.comparing(Installment::date));

        String conditions = prior.conditions() != null ? prior.conditions() : ConditionFilter.fromText(message);
        return new PromiseGate.Decision(true, new PromisePlan(all, conditions), List.of());
    }

    private static PromiseGate.Decision rejected(PromisePlan prior, String reason) {
        return new PromiseGate.Decision(false, prior, List.of(reason));
    }
}
