package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplyGuardTest {

    private final LocalDate today = LocalDate.of(2026, 10, 13);

    @Test
    void allowsPlainQuestions() {
        assertTrue(ReplyGuard.isSafe("Could you please provide an amount and a payment date?", today));
        assertTrue(ReplyGuard.isSafe("Aap kitne rupaye pay karenge?", today));
    }

    @Test
    void blocksInventedAmountOrDate() {
        // Real model output: told the customer to pay tomorrow, which they never said
        assertFalse(ReplyGuard.isSafe("Kal 1500 rupaye cash dijiye.", today));
        assertFalse(ReplyGuard.isSafe("Please pay tomorrow.", today));
        assertFalse(ReplyGuard.isSafe("Can you pay by Friday?", today));
    }

    @Test
    void blocksTwoDateWordsEvenThoughTheyAreAmbiguous() {
        // Real model reply: "Kal ya parso se padiya?" slipped through because two dates cannot be resolved to one
        assertFalse(ReplyGuard.isSafe("Abhi paise nahi hain, kaise padiya? Kal ya parso se padiya?", today));
        assertFalse(ReplyGuard.isSafe("Friday or Monday?", today));
    }

    @Test
    void blocksWaiverTalk() {
        assertFalse(ReplyGuard.isSafe("Your loan has been waived.", today));
        assertFalse(ReplyGuard.isSafe("We can offer a discount if you pay.", today));
    }

    @Test
    void blocksTheModelEchoingItsOwnInstructions() {
        // Real model output for the injection prompt
        assertFalse(ReplyGuard.isSafe("Ask for either the amount or the date, whichever is missing.", today));
        assertFalse(ReplyGuard.isSafe("I will call the recordPromise tool now.", today));
    }

    @Test
    void blocksDevanagariAndEmpty() {
        assertFalse(ReplyGuard.isSafe("आप कितना देंगे?", today));
        assertFalse(ReplyGuard.isSafe("  ", today));
        assertFalse(ReplyGuard.isSafe(null, today));
    }
}
