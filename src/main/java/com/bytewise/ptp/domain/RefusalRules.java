package com.bytewise.ptp.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects a customer refusing to pay, or giving no commitment at all ("nahi pata kab dunga").
 * Inability to pay right now ("abhi paise nahi hain") is deliberately not a refusal: it is the
 * start of a conversation about when they can pay.
 */
public final class RefusalRules {

    private static final Pattern REFUSAL = Pattern.compile(
            "\\bnahi\\s+(?:dunga|dungi|denge|de\\s+sakta|de\\s+sakti|de\\s+paunga|bharunga|bharungi|karunga|karungi"
                    + "|deto|denar|bharnar|karnar)\\b"
                    + "|\\b(?:pata|malum|maloom)\\s+nahi\\b"
                    + "|\\bnahi\\s+(?:pata|malum|maloom)\\b"
                    + "|\\b(?:won['’]?t|will\\s+not|not\\s+going\\s+to|can['’]?t|cannot|refuse|refusing)\\b"
                    + "(?:\\s+\\w+){0,2}\\s+pay\\b"
                    + "|\\bi\\s+(?:refuse|don['’]?t\\s+know\\s+when)\\b"
                    // "I am not going to give you the date", "I can't come in the date", "I do not have any date in mind"
                    + "|\\b(?:won['’]?t|will\\s+not|not\\s+going\\s+to|can['’]?t|cannot|refuse|refusing)"
                    + "\\s+(?:\\w+\\s+){0,4}(?:give|tell|say|commit|promise|come)\\b"
                    + "|\\b(?:no|any|not\\s+a)\\s+(?:date|idea)\\b.*\\b(?:mind|now|yet|today)\\b"
                    + "|\\b(?:do\\s+not|don['’]?t|dont)\\s+have\\s+(?:any\\s+)?(?:date|idea|money)\\b");

    private RefusalRules() {
    }

    public static boolean isRefusal(String customerMessage) {
        return customerMessage != null && REFUSAL.matcher(customerMessage.toLowerCase(Locale.ROOT)).find();
    }
}
