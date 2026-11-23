package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Never trust LLM output. These are plain, testable Java rules that catch
 * the kinds of inconsistencies small models produce. Some rules cross-check
 * the extraction against the transcript itself, because the customer's own
 * words (amount, weekday, day-of-month) are facts we can verify in code.
 */
public class PtpValidator {

    static final int MAX_DAYS_AHEAD = 90;

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern DAY_OF_MONTH = DateResolver.DAY_OF_MONTH;
    private static final Pattern TODAY_WORDS = Pattern.compile("\\b(today|aaj|aj|abhi)\\b");

    private static final Map<DayOfWeek, List<String>> WEEKDAY_WORDS = DateResolver.WEEKDAY_WORDS;

    public List<String> validate(PtpExtraction p, String transcript, LocalDate today) {
        return validate(p, transcript, today, false);
    }

    /**
     * @param amountDerived true when the amount was computed from the outstanding balance ("50%", "full"),
     *                      so it is not expected to appear in the transcript
     */
    public List<String> validate(PtpExtraction p, String transcript, LocalDate today, boolean amountDerived) {
        List<String> issues = new ArrayList<>();
        if (p == null) {
            issues.add("No extraction returned");
            return issues;
        }

        boolean hasDetails = p.amount() != null || p.date() != null;

        if (!p.ptpMade() && hasDetails) {
            issues.add("ptpMade=false but amount/date are filled in (inconsistent)");
        }
        if (p.ptpMade() && p.amount() == null) {
            issues.add("ptpMade=true but amount is missing");
        }
        if (p.ptpMade() && p.date() == null) {
            issues.add("ptpMade=true but date is missing");
        }
        if (p.amount() != null && p.amount().signum() <= 0) {
            issues.add("amount must be positive");
        }
        if (p.date() != null) {
            if (p.date().isBefore(today)) {
                issues.add("promised date " + p.date() + " is in the past");
            } else if (p.date().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
                issues.add("promised date " + p.date() + " is more than " + MAX_DAYS_AHEAD + " days away");
            }
        }
        if (p.conditions() != null && p.conditions().toLowerCase().contains("today is")) {
            issues.add("conditions looks copied from the prompt, not the customer");
        }

        if (transcript != null && !transcript.isBlank()) {
            String text = transcript.toLowerCase(Locale.ROOT);
            if (!amountDerived) {
                checkAmountInTranscript(p, text, issues);
            }
            checkDateAgainstTranscript(p, text, today, issues);
        }
        return issues;
    }

    private void checkAmountInTranscript(PtpExtraction p, String text, List<String> issues) {
        if (p.amount() == null || p.amount().signum() <= 0) {
            return; // already reported
        }
        Set<BigDecimal> spoken = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            try {
                spoken.add(new BigDecimal(m.group().replace(",", "")));
            } catch (NumberFormatException ignored) {
                // not a number after all
            }
        }
        boolean found = spoken.stream().anyMatch(n -> n.compareTo(p.amount()) == 0);
        if (!found) {
            issues.add("amount " + p.amount().stripTrailingZeros().toPlainString()
                    + " does not appear in the transcript");
        }
    }

    private void checkDateAgainstTranscript(PtpExtraction p, String text, LocalDate today, List<String> issues) {
        LocalDate date = p.date();
        if (date == null) {
            return;
        }

        for (Map.Entry<DayOfWeek, List<String>> e : WEEKDAY_WORDS.entrySet()) {
            boolean said = e.getValue().stream()
                    .anyMatch(w -> Pattern.compile("\\b" + w + "\\b").matcher(text).find());
            if (said && date.getDayOfWeek() != e.getKey()) {
                issues.add("customer said " + e.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT)
                        + ", but " + date + " is a "
                        + date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            }
        }

        Matcher dom = DAY_OF_MONTH.matcher(text);
        while (dom.find()) {
            int day = Integer.parseInt(dom.group(1));
            if (day >= 1 && day <= 31 && date.getDayOfMonth() != day) {
                issues.add("customer said day " + day + " but the date is the " + date.getDayOfMonth());
            }
        }

        if (date.equals(today) && !TODAY_WORDS.matcher(text).find()) {
            issues.add("date equals today but the customer never said today (model may have defaulted)");
        }
    }
}
