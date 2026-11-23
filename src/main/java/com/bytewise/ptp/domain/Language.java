package com.bytewise.ptp.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The language the customer is writing in. Transcripts are Roman script, so detection is by common words. */
public enum Language {
    ENGLISH("English"),
    HINGLISH("Hinglish (Hindi written in Roman letters)"),
    MARATHI("Marathi written in Roman letters");

    // Words that are Marathi and not also Hindi. Shared words (nahi, weekday names) are left out.
    private static final Pattern MARATHI_WORDS = Pattern.compile(
            "\\b(tarkhela|deto|dete|denar|dyal|honar|tevha|tevhaa|saheb|pagar|aahe|ahe|mi|mala|tumhi|tumhala"
                    + "|kiti|karto|karte|karnar|bharnar|kasa|kadhi|pudhchya|aathvadyat"
                    + "|la|cha|chi|che|purna|nantar|dyayche|bharto|aahet)\\b");

    private static final Pattern HINDI_WORDS = Pattern.compile(
            "\\b(dunga|dungi|denge|karunga|karungi|tarikh|tareekh|hai|hoon|hun|kal|parso|parson|agle|baad|paise"
                    + "|mujhe|main|aap|bhej|kab|abhi|ke|ko|tak|rupaye)\\b");

    private final String promptName;

    Language(String promptName) {
        this.promptName = promptName;
    }

    /** Name as it should appear in an instruction to the model. */
    public String promptName() {
        return promptName;
    }

    public static Language detect(String text) {
        if (text == null || text.isBlank()) {
            return ENGLISH;
        }
        String t = text.toLowerCase(Locale.ROOT);
        int marathi = count(MARATHI_WORDS, t);
        int hindi = count(HINDI_WORDS, t);
        if (marathi == 0 && hindi == 0) {
            return ENGLISH;
        }
        return marathi > hindi ? MARATHI : HINGLISH;
    }

    private static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
