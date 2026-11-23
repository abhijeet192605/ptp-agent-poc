package com.bytewise.ptp.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Payment mode read from the customer's words; the model often leaves it out. */
public final class ModeResolver {

    private static final Map<String, Pattern> MODES = new LinkedHashMap<>();

    static {
        MODES.put("UPI", Pattern.compile("\\b(upi|gpay|google\\s*pay|phonepe|paytm|bhim)\\b"));
        MODES.put("CHEQUE", Pattern.compile("\\b(cheque|check|chq)\\b"));
        MODES.put("BANK_TRANSFER", Pattern.compile("\\b(bank\\s*transfer|neft|rtgs|imps|net\\s*banking)\\b"));
        MODES.put("CASH", Pattern.compile("\\b(cash|nakad|nagad|rokh|rokda)\\b"));
    }

    private ModeResolver() {
    }

    /** @return the single mode mentioned, or null if none or more than one */
    public static String fromText(String customerText) {
        if (customerText == null) {
            return null;
        }
        String t = customerText.toLowerCase(Locale.ROOT);
        String found = null;
        for (Map.Entry<String, Pattern> e : MODES.entrySet()) {
            if (e.getValue().matcher(t).find()) {
                if (found != null) {
                    return null;
                }
                found = e.getKey();
            }
        }
        return found;
    }
}
