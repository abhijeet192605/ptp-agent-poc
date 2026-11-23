package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gate with a known outstanding balance. */
class PromiseGateOutstandingTest {

    private final PromiseGate gate = new PromiseGate();
    private final LocalDate today = LocalDate.of(2026, 10, 13); // Tuesday
    private final BigDecimal outstanding = new BigDecimal("10000.00");

    @Test
    void fullAmountMarathiExample() {
        // Real test message from the user: "20 tarikh la purna amount cha cheque denar."
        var text = "20 tarikh la purna amount cha cheque denar.";
        var d = gate.check(null, null, null, null, text, text, outstanding, today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(0, outstanding.compareTo(d.promise().amount()));
        assertEquals(LocalDate.of(2026, 10, 20), d.promise().date());
        assertEquals("CHEQUE", d.promise().mode());
    }

    @Test
    void percentageIsComputedWhateverTheModelProposed() {
        var text = "I will pay 50% of the outstanding by Friday";
        // the model grabbed the "50" from the text as a rupee amount
        var d = gate.check(new BigDecimal("50"), LocalDate.of(2026, 10, 15), null, null, text, text, outstanding, today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(0, new BigDecimal("5000").compareTo(d.promise().amount()));
        assertEquals(LocalDate.of(2026, 10, 16), d.promise().date());
    }

    @Test
    void percentageNeedsAKnownBalance() {
        var text = "50% dunga kal";
        var d = gate.check(null, null, null, null, text, text, null, today);
        assertFalse(d.accepted());
        assertTrue(d.reasons().contains("amount is missing"), d.reasons().toString());
    }

    @Test
    void theModelsGuessesForDateAndModeAreNotUsed() {
        // Real test on ACC1002 (owes 25000): the model offered 2 Nov and UPI; the customer said neither
        var text = "I will pay 50% amount by day after tomorrow.";
        var d = gate.check(null, LocalDate.of(2026, 11, 2), "UPI", null, text, text, new BigDecimal("25000"), today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(0, new BigDecimal("12500").compareTo(d.promise().amount()));
        assertEquals(today.plusDays(2), d.promise().date());
        assertNull(d.promise().mode());
    }

    @Test
    void aModeTheCustomerNamedIsStillRecorded() {
        var text = "Parso 3000 UPI se dunga";
        var d = gate.check(new BigDecimal("3000"), null, "CASH", null, text, text, outstanding, today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals("UPI", d.promise().mode());
    }

    @Test
    void aPercentageIsNeverTakenAsRupeesWhenTheBalanceIsUnknown() {
        // Seen live: the model proposed amount 50 for "50% dunga kal" and Rs 50 was saved
        var text = "50% dunga kal";
        var d = gate.check(new BigDecimal("50"), LocalDate.of(2026, 10, 14), null, null, text, text, null, today);
        assertFalse(d.accepted());
        assertTrue(d.reasons().contains("amount is missing"), d.reasons().toString());
    }

    @Test
    void explicitRupeeFigureStillWorksWithABalance() {
        var text = "Kal 2000 UPI se bhej dunga";
        var d = gate.check(new BigDecimal("2000"), null, null, null, text, text, outstanding, today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(0, new BigDecimal("2000").compareTo(d.promise().amount()));
        assertEquals("UPI", d.promise().mode());
    }

    @Test
    void shareWithoutADateIsRejected() {
        var text = "aadha dunga";
        var d = gate.check(null, null, null, null, text, text, outstanding, today);
        assertFalse(d.accepted());
        assertTrue(d.reasons().contains("date is missing"), d.reasons().toString());
    }

    @Test
    void shareStatedAcrossTurnsIsCombined() {
        var d = gate.check(null, null, null, null, "Friday", "poora dunga. Friday", outstanding, today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(0, outstanding.compareTo(d.promise().amount()));
        assertEquals(LocalDate.of(2026, 10, 16), d.promise().date());
    }
}
