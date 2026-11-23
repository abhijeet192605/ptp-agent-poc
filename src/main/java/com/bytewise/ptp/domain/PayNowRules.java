package com.bytewise.ptp.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises a customer who wants to pay right now ("I will pay now, send me the payment link"), and the channel
 * they ask for. "Abhi paise nahi hain" (no money right now) is the opposite and is never a request.
 */
public final class PayNowRules {

    /** @param requested true when the customer wants to pay now or asks for a link; channel may be null */
    public record Request(boolean requested, Channel channel) {
        static final Request NONE = new Request(false, null);
    }

    private static final Pattern NOW = Pattern.compile(
            "\\b(now|right now|abhi|abhi ke abhi|turant|immediately|ata|atta)\\b");
    private static final Pattern PAY = Pattern.compile(
            "\\b(pay|paying|payment|dunga|dungi|denge|karunga|karungi|karta|bhej|bhejo|bhejna|bharto|bharen|bharin"
                    + "|deto|denar|karto|kartoy|transfer|clear)\\b");
    private static final Pattern LINK = Pattern.compile("\\b(link|lnk)\\b");
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(no|not|nahi|nahin|nako|cannot|can't|cant|won't|wont|nope|dont|don't)\\b");

    private static final Pattern WHATSAPP = Pattern.compile("\\b(whatsapp|whats app|whatsap|watsapp)\\b");
    private static final Pattern SMS = Pattern.compile("\\b(sms|text message|text)\\b");

    private PayNowRules() {
    }

    public static Request detect(String customerMessage) {
        if (customerMessage == null || customerMessage.isBlank()) {
            return Request.NONE;
        }
        String t = customerMessage.toLowerCase(Locale.ROOT);
        if (NEGATION.matcher(t).find()) {
            return Request.NONE;
        }
        boolean payNow = NOW.matcher(t).find() && PAY.matcher(t).find();
        boolean linkAsked = LINK.matcher(t).find();
        if (!payNow && !linkAsked) {
            return Request.NONE;
        }
        boolean wa = WHATSAPP.matcher(t).find();
        boolean sms = SMS.matcher(t).find();
        Channel channel = wa == sms ? null : (wa ? Channel.WHATSAPP : Channel.SMS);
        return new Request(true, channel);
    }
}
