package com.bytewise.ptp.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Wording that must reach a human regardless of what the model decides: requests for a person,
 * complaints and legal or harassment language. A 3B model cannot be relied on to notice these.
 */
public final class EscalationRules {

    private static final Pattern TRIGGERS = Pattern.compile(
            "\\b(manager|supervisor|complaint|lawyer|advocate|court|police|harass\\w*|stop calling)\\b");

    private EscalationRules() {
    }

    public static boolean requiresHuman(String customerMessage) {
        return customerMessage != null && TRIGGERS.matcher(customerMessage.toLowerCase(Locale.ROOT)).find();
    }
}
