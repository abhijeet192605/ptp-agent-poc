package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads a split payment out of one message: "50% on the 20th by cheque and 50% on the 22nd by cash".
 * The message is cut at "and / ani / aur / then" and each part is read on its own with the same amount,
 * date and mode rules used for a single promise.
 */
public final class InstallmentParser {

    private static final Pattern SPLIT = Pattern.compile(
            "\\b(?:ani|aur|and|then|phir|tyanantar|tatha)\\b|\\s\\+\\s|[,;]");

    /** One part of the message. */
    public record Segment(String text, BigDecimal amount, boolean amountDerived, LocalDate date, String mode) {

        boolean complete() {
            return amount != null && date != null;
        }

        boolean hasPaymentInfo() {
            return amount != null || date != null;
        }
    }

    /**
     * @param complete parts that state both an amount and a date
     * @param partial  parts that state only one of them
     */
    public record Parsed(List<Segment> complete, List<Segment> partial) {

        /** Two or more complete parts make a split payment. */
        public boolean isPlan() {
            return complete.size() >= 2;
        }
    }

    private InstallmentParser() {
    }

    public static Parsed parse(String message, BigDecimal outstanding, LocalDate today) {
        List<Segment> complete = new ArrayList<>();
        List<Segment> partial = new ArrayList<>();
        if (message == null || message.isBlank()) {
            return new Parsed(complete, partial);
        }
        for (String part : SPLIT.split(message.toLowerCase(Locale.ROOT))) {
            if (part.isBlank()) {
                continue;
            }
            var relative = AmountResolver.resolve(part, outstanding);
            BigDecimal amount = relative.or(() -> AmountResolver.statedAmount(part)).orElse(null);
            LocalDate date = DateResolver.resolve(part, today).orElse(null);
            Segment s = new Segment(part.trim(), amount, relative.isPresent(), date, ModeResolver.fromText(part));
            if (s.complete()) {
                complete.add(s);
            } else if (s.hasPaymentInfo() || AmountResolver.hasStatedNumber(part)
                    || AmountResolver.mentionsRelativeAmount(part)) {
                // Numbers we could not read must fail the plan, never vanish from it.
                partial.add(s);
            }
        }
        return new Parsed(complete, partial);
    }

    public static boolean looksLikePlan(String message, BigDecimal outstanding, LocalDate today) {
        return parse(message, outstanding, today).isPlan();
    }
}
