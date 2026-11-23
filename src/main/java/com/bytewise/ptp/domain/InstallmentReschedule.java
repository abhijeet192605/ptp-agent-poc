package com.bytewise.ptp.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The customer moves an installment of the promise on record: "my next installment will be delayed by 2 days, that is
 * on the 23rd", "I need one more day", "I will pay next day rather than the 14th", or just "I will pay on the 15th".
 * Which installment is meant is worked out from their words, and the new date from the date they name or the delay they
 * state. The amount never changes and is never asked for again.
 */
public final class InstallmentReschedule {

    public enum Kind {
        /** The plan was changed. */
        RESCHEDULED,
        /** More than one installment could be meant and the words do not say which. */
        ASK_WHICH,
        /** The installment is clear but no usable new date was given. */
        ASK_NEW_DATE,
        /** Nothing on record can be moved. */
        NOT_POSSIBLE
    }

    /** @param plan the new plan when RESCHEDULED, otherwise the plan on record */
    public record Outcome(Kind kind, PromisePlan plan) {
    }

    private static final String NUMBER = "\\d{1,3}|an?|one|two|three|four|five|six|seven|eight|nine|ten|couple of";
    private static final String UNIT = "days?|din|weeks?|hafte|hafta|aathvad\\w*";

    private static final Pattern MOVE = Pattern.compile(
            "\\b(delay\\w*|postpone\\w*|push\\w*|shift\\w*|move\\w*|reschedul\\w*|extend\\w*|extension|prepone\\w*"
                    + "|defer\\w*|aage\\s+badh\\w*|der\\s+se|tal\\w*)\\b");
    private static final Pattern THING = Pattern.compile(
            "\\b(installment|instalment|payment|emi|kist|kisht|hapta|date|one)\\b");
    /** "2 days", "a week", "one day later": a number and a unit. */
    private static final Pattern DELTA = Pattern.compile("\\b(" + NUMBER + ")\\s*(" + UNIT + ")\\b");
    /** "one more additional day", "an extra day", "another day", "2 more days": a number is optional. */
    private static final Pattern EXTRA = Pattern.compile(
            "\\b(?:(" + NUMBER + ")\\s+)?(?:more|extra|additional|another)(?:\\s+(?:more|extra|additional))?\\s+("
                    + UNIT + ")\\b");
    /** "one day later", "2 days more": a number and a unit followed by later, late or more. */
    private static final Pattern LATER = Pattern.compile("\\b(" + NUMBER + ")\\s*(" + UNIT + ")\\s+(?:later|late|more)\\b");
    private static final Pattern NEXT_DAY = Pattern.compile("\\bnext\\s+day\\b");
    /** "rather than 14", "instead of the 14th": the day of the month the installment is on now. */
    private static final Pattern OLD_DAY = Pattern.compile(
            "\\b(?:rather\\s+than|instead\\s+of|in\\s+place\\s+of|not\\s+on)\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\b");
    private static final Pattern ORDINAL = Pattern.compile(
            "\\b(first|1st|pehla|pehli|pahila|pahili|second|2nd|dusra|dusri|doosra|doosri|dusara|third|3rd|teesra|tisra"
                    + "|last|final|aakhri|akhri|shevtcha)\\b");

    private static final Map<String, Integer> WORDS = Map.ofEntries(
            Map.entry("a", 1), Map.entry("an", 1), Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3),
            Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7),
            Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("couple of", 2));

    private InstallmentReschedule() {
    }

    /**
     * True when the customer talks about moving a payment: a moving word with an installment, an ordinal or a delay;
     * or an extra day or "next day"; or "rather than the 14th".
     */
    public static boolean mentionsReschedule(String customerMessage) {
        if (customerMessage == null) {
            return false;
        }
        String t = customerMessage.toLowerCase(Locale.ROOT);
        if (EXTRA.matcher(t).find() || LATER.matcher(t).find() || NEXT_DAY.matcher(t).find()
                || OLD_DAY.matcher(t).find()) {
            return true;
        }
        return MOVE.matcher(t).find()
                && (THING.matcher(t).find() || ORDINAL.matcher(t).find() || DELTA.matcher(t).find());
    }

    /**
     * True when the message is just a new date with no amount, such as "I am going to pay on 15th". Said after the
     * promise on record was put to the customer, it means the same amounts on that date.
     */
    public static boolean isNewDateOnly(String customerMessage, LocalDate today) {
        return customerMessage != null
                && DateResolver.resolve(customerMessage, today).isPresent()
                && !AmountResolver.hasStatedNumber(customerMessage)
                && !AmountResolver.mentionsRelativeAmount(customerMessage);
    }

    public static Outcome apply(PromisePlan prior, String customerMessage, LocalDate today) {
        // Installments dated before today are in the past: they are not part of what can still be moved.
        List<Installment> due = prior.installments().stream()
                .filter(i -> !i.date().isBefore(today))
                .sorted(Comparator.comparing(Installment::date))
                .toList();
        if (due.isEmpty()) {
            return new Outcome(Kind.NOT_POSSIBLE, prior);
        }

        String t = customerMessage.toLowerCase(Locale.ROOT);
        Integer delayDays = delayDays(t);
        LocalDate named = DateResolver.resolve(t, today).orElse(null);
        Integer oldDay = oldDayOfMonth(t);

        Installment target = null;
        Integer ordinal = ordinalIndex(t, due.size());
        if (ordinal != null) {
            if (ordinal >= due.size()) {
                return new Outcome(Kind.ASK_WHICH, prior);
            }
            target = due.get(ordinal);
        } else if (oldDay != null) {
            // "rather than the 14th": the installment that is on the 14th now
            List<Installment> matches = due.stream().filter(i -> i.date().getDayOfMonth() == oldDay).toList();
            if (matches.size() == 1) {
                target = matches.get(0);
            }
        } else if (delayDays != null && named != null) {
            // "delayed by 2 days, that is on the 23rd": the installment whose date plus 2 days is the 23rd
            List<Installment> matches = new ArrayList<>();
            for (Installment i : due) {
                if (i.date().plusDays(delayDays).equals(named)) {
                    matches.add(i);
                }
            }
            if (matches.size() == 1) {
                target = matches.get(0);
            }
        }
        if (target == null && due.size() == 1) {
            target = due.get(0);
        }
        if (target == null) {
            return new Outcome(Kind.ASK_WHICH, prior);
        }

        LocalDate newDate = named != null ? named : (delayDays != null ? target.date().plusDays(delayDays) : null);
        if (newDate == null || newDate.isBefore(today) || newDate.isAfter(today.plusDays(PtpValidator.MAX_DAYS_AHEAD))
                || newDate.equals(target.date())) {
            return new Outcome(Kind.ASK_NEW_DATE, prior);
        }

        List<Installment> all = new ArrayList<>(due);
        all.remove(target);
        all.add(new Installment(target.amount(), newDate, target.mode()));
        all.sort(Comparator.comparing(Installment::date));
        return new Outcome(Kind.RESCHEDULED, new PromisePlan(all, prior.conditions()));
    }

    /** The delay in days the customer states ("2 days", "a week", "one more day", "next day"), or null. */
    private static Integer delayDays(String text) {
        Set<Integer> found = new HashSet<>();
        for (Pattern p : List.of(DELTA, EXTRA)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String n = m.group(1);
                int count = n == null ? 1 : (n.chars().allMatch(Character::isDigit) ? Integer.parseInt(n) : WORDS.getOrDefault(n, 0));
                if (count <= 0) {
                    continue;
                }
                String unit = m.group(2);
                boolean weeks = unit.startsWith("week") || unit.startsWith("hafte") || unit.startsWith("hafta")
                        || unit.startsWith("aathvad");
                found.add(weeks ? count * 7 : count);
            }
        }
        if (NEXT_DAY.matcher(text).find()) {
            found.add(1);
        }
        return found.size() == 1 ? found.iterator().next() : null; // two different delays are ambiguous
    }

    private static Integer oldDayOfMonth(String text) {
        Matcher m = OLD_DAY.matcher(text);
        if (!m.find()) {
            return null;
        }
        int day = Integer.parseInt(m.group(1));
        return day >= 1 && day <= 31 ? day : null;
    }

    /** The zero-based position of the installment named by an ordinal ("second", "last"), or null if none. */
    private static Integer ordinalIndex(String text, int size) {
        Matcher m = ORDINAL.matcher(text);
        if (!m.find()) {
            return null;
        }
        String w = m.group(1);
        if (w.matches("first|1st|pehla|pehli|pahila|pahili")) {
            return 0;
        }
        if (w.matches("second|2nd|dusra|dusri|doosra|doosri|dusara")) {
            return 1;
        }
        if (w.matches("third|3rd|teesra|tisra")) {
            return 2;
        }
        return size - 1; // last, final, aakhri
    }
}
