package com.bytewise.ptp.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks the model's free-text reply before it reaches the customer. A small model may answer in the
 * wrong script, state an amount or date the customer never gave, or talk about waiving the loan.
 * Anything unsafe is replaced by a fixed question.
 */
public final class ReplyGuard {

    private static final int MAX_LENGTH = 300;

    private static final Pattern NON_LATIN_LETTER = Pattern.compile("[^\\p{IsLatin}\\P{IsAlphabetic}]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern MONEY_TALK = Pattern.compile(
            "\\b(waive\\w*|write[- ]?off|written\\s+off|discount|forgive\\w*|settle\\w*|no\\s+need\\s+to\\s+pay"
                    + "|reduce\\w*|cancel\\w*)\\b"
                    // the model repeating its own instructions instead of speaking to the customer
                    + "|\\b(ask\\s+for|the\\s+customer|tool|recordpromise|escalatetohuman|instructions?"
                    + "|system\\s+prompt|whichever\\s+is\\s+missing)\\b");

    private ReplyGuard() {
    }

    public static boolean isSafe(String reply, LocalDate today) {
        if (reply == null || reply.isBlank() || reply.length() > MAX_LENGTH) {
            return false;
        }
        String r = reply.toLowerCase(Locale.ROOT);
        return !NON_LATIN_LETTER.matcher(reply).find()
                && !DIGIT.matcher(reply).find()
                && !MONEY_TALK.matcher(r).find()
                && !DateResolver.hasDateClue(r, today); // any date wording, even "kal ya parso" which is ambiguous
    }
}
