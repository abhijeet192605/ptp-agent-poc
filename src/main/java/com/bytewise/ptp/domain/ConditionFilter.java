package com.bytewise.ptp.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Small models invent or garble the free-text "conditions" ("after Friday", "pay the bill"). Instead of
 * trusting them, conditions are read from the customer's own words against a fixed list of real
 * dependencies. Anything else is null.
 */
public final class ConditionFilter {

    private static final Map<String, Pattern> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put("salary", Pattern.compile("\\b(salary|pagar|tankhwah|tankhah|vetan)\\b"));
        KNOWN.put("wedding", Pattern.compile("\\b(wedding|shaadi|shadi|lagn)\\b"));
        KNOWN.put("harvest", Pattern.compile("\\b(harvest|fasal|fasl|kapni|crop)\\b"));
        KNOWN.put("bonus", Pattern.compile("\\b(bonus)\\b"));
        KNOWN.put("festival", Pattern.compile("\\b(festival|diwali|tyohar|dussehra|eid)\\b"));
    }

    private ConditionFilter() {
    }

    /** @return the known dependencies mentioned in the text, comma-separated, or null if none */
    public static String fromText(String customerText) {
        if (customerText == null || customerText.isBlank()) {
            return null;
        }
        String t = customerText.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        KNOWN.forEach((label, pattern) -> {
            if (pattern.matcher(t).find()) {
                found.add(label);
            }
        });
        return found.isEmpty() ? null : String.join(", ", found);
    }
}
