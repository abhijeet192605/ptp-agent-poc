package com.bytewise.ptp.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves dates the customer stated explicitly (weekday, day-of-month, today/tomorrow)
 * with plain calendar arithmetic, which small LLMs get wrong. Returns empty when the
 * transcript has no such clue or is ambiguous, so the caller falls back to the model.
 */
public final class DateResolver {

    static final Pattern DAY_OF_MONTH =
            Pattern.compile("\\b(\\d{1,2})\\s*(?:tarikh|tareekh|tarkhela|tarkhe|tarakh|date|dt|st|nd|rd|th|ko|ke|la|tak)\\b");

    static final Map<DayOfWeek, List<String>> WEEKDAY_WORDS = Map.of(
            DayOfWeek.MONDAY, List.of("monday", "somvar", "somwar", "somvaar", "somvara"),
            DayOfWeek.TUESDAY, List.of("tuesday", "mangalvar", "mangalwar", "mangalvaar", "mangal"),
            DayOfWeek.WEDNESDAY, List.of("wednesday", "budhvar", "budhwar", "budhvaar", "budh"),
            DayOfWeek.THURSDAY, List.of("thursday", "guruvar", "guruwar", "guruvaar", "brihaspativar", "veervar", "virvar"),
            DayOfWeek.FRIDAY, List.of("friday", "shukravar", "shukrawar", "shukravaar", "shukrvar"),
            DayOfWeek.SATURDAY, List.of("saturday", "shanivar", "shaniwar", "shanivaar", "shani"),
            DayOfWeek.SUNDAY, List.of("sunday", "ravivar", "raviwar", "itwar", "ravivaar", "itvar"));

    private static final Pattern TODAY = Pattern.compile("\\b(today|aaj|aj)\\b");
    // The customer is delinquent (unpaid until today), so "kal" means tomorrow, never yesterday.
    private static final Pattern TOMORROW = Pattern.compile("\\b(tomorrow|kal)\\b");
    private static final Pattern DAY_AFTER_TOMORROW =
            Pattern.compile("\\b(parso|parson|parsu|day after tomorrow)\\b");
    private static final Pattern NEXT_WEEK =
            Pattern.compile("\\b(next week|agle (?:hafte|hafta|haftey|week)|pudhchya (?:aathvadyat|week))\\b");
    private static final Pattern MONTH_END = Pattern.compile(
            "\\b(month[ -]?end|mahine (?:ke )?(?:end|ant|aakhir|akhir)|mahine ke last|mahinyachya shevti)\\b");
    /** Business rule: month end is taken as the 28th, which exists in every month. */
    private static final int MONTH_END_DAY = 28;

    private DateResolver() {
    }

    /** Replaces the model's date with the one computed from the transcript, when there is one. */
    public static PtpExtraction applyTo(PtpExtraction p, String transcript, LocalDate today) {
        if (p == null || !p.ptpMade()) {
            return p;
        }
        return resolve(transcript, today)
                .map(d -> new PtpExtraction(p.ptpMade(), p.amount(), d, p.mode(), p.conditions()))
                .orElse(p);
    }

    public static Optional<LocalDate> resolve(String transcript, LocalDate today) {
        Set<LocalDate> candidates = candidates(transcript, today);
        return candidates.size() == 1 ? Optional.of(candidates.iterator().next()) : Optional.empty();
    }

    /** True if the text contains any date wording at all, even when it names two dates and so cannot be resolved. */
    public static boolean hasDateClue(String transcript, LocalDate today) {
        return !candidates(transcript, today).isEmpty();
    }

    private static Set<LocalDate> candidates(String transcript, LocalDate today) {
        Set<LocalDate> candidates = new LinkedHashSet<>();
        if (transcript == null || transcript.isBlank()) {
            return candidates;
        }
        String text = transcript.toLowerCase(Locale.ROOT);

        Matcher dom = DAY_OF_MONTH.matcher(text);
        while (dom.find()) {
            int day = Integer.parseInt(dom.group(1));
            if (day >= 1 && day <= 31) {
                candidates.add(nextDayOfMonth(day, today));
            }
        }
        WEEKDAY_WORDS.forEach((dow, words) -> {
            boolean said = words.stream().anyMatch(w -> Pattern.compile("\\b" + w + "\\b").matcher(text).find());
            if (said) {
                candidates.add(today.with(TemporalAdjusters.next(dow)));
            }
        });
        if (TODAY.matcher(text).find()) {
            candidates.add(today);
        }
        // "day after tomorrow" contains the word "tomorrow": take it out first, or one phrase would read as two dates
        if (TOMORROW.matcher(DAY_AFTER_TOMORROW.matcher(text).replaceAll(" ")).find()) {
            candidates.add(today.plusDays(1));
        }
        if (DAY_AFTER_TOMORROW.matcher(text).find()) {
            candidates.add(today.plusDays(2));
        }
        if (NEXT_WEEK.matcher(text).find()) {
            // 7 days from today or the coming Monday, whichever is later
            LocalDate plusSeven = today.plusDays(7);
            LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            candidates.add(plusSeven.isAfter(nextMonday) ? plusSeven : nextMonday);
        }
        if (MONTH_END.matcher(text).find()) {
            candidates.add(nextDayOfMonth(MONTH_END_DAY, today));
        }

        return candidates;
    }

    /** The first date on or after today whose day-of-month is {@code day}. */
    private static LocalDate nextDayOfMonth(int day, LocalDate today) {
        LocalDate month = today.withDayOfMonth(1);
        for (int i = 0; i < 13; i++) {
            if (day <= month.lengthOfMonth()) {
                LocalDate candidate = month.withDayOfMonth(day);
                if (!candidate.isBefore(today)) {
                    return candidate;
                }
            }
            month = month.plusMonths(1);
        }
        throw new IllegalStateException("unreachable: day " + day);
    }
}
