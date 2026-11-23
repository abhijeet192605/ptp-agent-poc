package com.bytewise.ptp.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises a customer who says they have already given a promise ("maine already date bata di thi in last
 * call"), and a plain yes to "is that still correct?". Both are about a promise already on record, not a new one.
 */
public final class PriorPromiseRules {

    private static final Pattern EARLIER = Pattern.compile(
            "\\b(already|last call|previous call|earlier call|pichle call|pichli call|pichhle call|pichli baar"
                    + "|pichle baar|bata diya|bata di thi|bata chuka|bata chuki|bataya tha|sangitle|sangitla"
                    + "|sangitali|aadhich|adhich)\\b");

    private static final Pattern YES = Pattern.compile(
            "^\\W*(yes|yeah|yep|yup|ok|okay|sure|correct|right|haan|han|haa|ji|theek|thik|sahi|barobar|hoy|ho)\\b");
    private static final Pattern NO = Pattern.compile(
            "\\b(no|not|nahi|nahin|nako|change|badal|badlo|badalna|badalaycha)\\b");

    private static final Pattern PARTIAL = Pattern.compile(
            "\\b(partially|partly|mostly|not exactly|not fully|not entirely|aanshik|thoda sahi|thoda alag|kuch badlav)\\b");

    private static final int MAX_WORDS_FOR_YES = 6;

    private PriorPromiseRules() {
    }

    /** True when the customer refers to something they said earlier. */
    public static boolean mentionsEarlierPromise(String customerMessage) {
        return customerMessage != null && EARLIER.matcher(customerMessage.toLowerCase(Locale.ROOT)).find();
    }

    /** "It is partially correct": some of the promise on record is right and some of it has changed. */
    public static boolean isPartialAgreement(String customerMessage) {
        return customerMessage != null && PARTIAL.matcher(customerMessage.toLowerCase(Locale.ROOT)).find();
    }

    /** A short, plain yes: no number, no negation, nothing else being proposed. */
    public static boolean isPlainYes(String customerMessage) {
        if (customerMessage == null || customerMessage.isBlank()) {
            return false;
        }
        String t = customerMessage.toLowerCase(Locale.ROOT).trim();
        return t.split("\\s+").length <= MAX_WORDS_FOR_YES
                && YES.matcher(t).find()
                && !NO.matcher(t).find()
                && t.chars().noneMatch(Character::isDigit);
    }
}
