package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out amounts the customer states relative to the outstanding balance ("full", "half", "50%",
 * "purna amount") with plain arithmetic, because small models cannot be trusted with percentages.
 * Returns empty when the customer also named a rupee figure, since the intent is then ambiguous.
 */
public final class AmountResolver {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Pattern PERCENT = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d+)?)\\s*(?:%|percent|per\\s*cent|pratishat|pratisht|takke|taka)");
    private static final Pattern HALF = Pattern.compile("\\b(half|aadha|adha|aadhi|adhi|ardha|ardhi)\\b");
    private static final Pattern QUARTER = Pattern.compile("\\b(quarter|chauthai|chauthaai)\\b");
    private static final Pattern FULL = Pattern.compile(
            "\\b(full|poora|pura|puri|purna|purn|whole|entire|total|saara|sara|sampurna)\\b");

    private AmountResolver() {
    }

    /** Whether the text refers to a share of the balance at all, whether or not we know the balance. */
    public static boolean mentionsRelativeAmount(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        return PERCENT.matcher(t).find() || HALF.matcher(t).find()
                || QUARTER.matcher(t).find() || FULL.matcher(t).find();
    }

    /** Whether the customer named a rupee figure (any number that is not a percentage or a day of the month). */
    public static boolean hasStatedNumber(String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        t = PERCENT.matcher(t).replaceAll(" ");
        t = DateResolver.DAY_OF_MONTH.matcher(t).replaceAll(" ");
        return t.chars().anyMatch(Character::isDigit);
    }

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    /** The rupee figure the customer named, if they named exactly one. */
    public static Optional<BigDecimal> statedAmount(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String t = text.toLowerCase(Locale.ROOT);
        t = PERCENT.matcher(t).replaceAll(" ");
        t = DateResolver.DAY_OF_MONTH.matcher(t).replaceAll(" ");
        Set<BigDecimal> found = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(t);
        while (m.find()) {
            try {
                found.add(new BigDecimal(m.group().replace(",", "")));
            } catch (NumberFormatException ignored) {
                // not a number after all
            }
        }
        return found.size() == 1 ? Optional.of(found.iterator().next()) : Optional.empty();
    }

    public static Optional<BigDecimal> resolve(String text, BigDecimal outstanding) {
        if (text == null || outstanding == null || outstanding.signum() <= 0 || hasStatedNumber(text)) {
            return Optional.empty();
        }
        String t = text.toLowerCase(Locale.ROOT);

        Set<BigDecimal> percents = new LinkedHashSet<>();
        Matcher m = PERCENT.matcher(t);
        while (m.find()) {
            BigDecimal p = new BigDecimal(m.group(1));
            if (p.signum() <= 0 || p.compareTo(HUNDRED) > 0) {
                return Optional.empty(); // "150%" of a balance makes no sense
            }
            percents.add(p);
        }
        if (percents.isEmpty()) {
            if (HALF.matcher(t).find()) {
                percents.add(BigDecimal.valueOf(50));
            }
            if (QUARTER.matcher(t).find()) {
                percents.add(BigDecimal.valueOf(25));
            }
            if (FULL.matcher(t).find()) {
                percents.add(HUNDRED);
            }
        }
        if (percents.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(outstanding.multiply(percents.iterator().next())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP));
    }
}
