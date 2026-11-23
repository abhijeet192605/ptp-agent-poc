package com.bytewise.ptp.domain;

import com.bytewise.ptp.domain.Replies.Ask;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Decides the agent's next question when the customer has not given a usable promise
 * (vague, conditional such as "after salary", or only half the details).
 */
public final class FollowUp {

    public static final String ASK_BOTH = Replies.ask(Ask.BOTH, Language.ENGLISH);
    public static final String ASK_DATE = Replies.ask(Ask.DATE, Language.ENGLISH);
    public static final String ASK_AMOUNT = Replies.ask(Ask.AMOUNT, Language.ENGLISH);

    private FollowUp() {
    }

    /** What is still missing, or empty if the promise is complete. */
    public static Optional<Ask> askFor(PtpExtraction p, LocalDate today) {
        if (p == null) {
            return Optional.empty();
        }
        boolean hasAmount = p.ptpMade() && p.amount() != null && p.amount().signum() > 0;
        // A date the validator would reject (past, or absurdly far away) is as good as no date.
        boolean hasDate = p.ptpMade() && p.date() != null
                && !p.date().isBefore(today)
                && !p.date().isAfter(today.plusDays(PtpValidator.MAX_DAYS_AHEAD));
        if (hasAmount && hasDate) {
            return Optional.empty();
        }
        if (hasAmount) {
            return Optional.of(Ask.DATE);
        }
        if (hasDate) {
            return Optional.of(Ask.AMOUNT);
        }
        return Optional.of(Ask.BOTH);
    }

    /** The question in English, for the analyze endpoint. */
    public static Optional<String> questionFor(PtpExtraction p, LocalDate today) {
        return askFor(p, today).map(a -> Replies.ask(a, Language.ENGLISH));
    }
}
