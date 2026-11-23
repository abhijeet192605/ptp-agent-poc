package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefusalRulesTest {

    @Test
    void detectsRefusalsAndNoCommitment() {
        assertTrue(RefusalRules.isRefusal("Nahi dunga."));
        assertTrue(RefusalRules.isRefusal("Mujhe nahi pata kab dunga."));
        assertTrue(RefusalRules.isRefusal("Pata nahi, kab de sakunga"));
        assertTrue(RefusalRules.isRefusal("I won't pay anything"));
        assertTrue(RefusalRules.isRefusal("I am not going to pay"));
        assertTrue(RefusalRules.isRefusal("Mi nahi deto"));
    }

    @Test
    void refusingToNameADateIsARefusal() {
        assertTrue(RefusalRules.isRefusal("I can't come in the date"));
        assertTrue(RefusalRules.isRefusal("no I do not have any date in my mind"));
        assertTrue(RefusalRules.isRefusal("I have told you that I am not going to give you the date right now"));
        assertTrue(RefusalRules.isRefusal("I told you that I am not going to give you the date"));
        assertFalse(RefusalRules.isRefusal("I will give you 5000 on Friday"));
    }

    @Test
    void dateWordAfterDayNumberIsADate() {
        var today = java.time.LocalDate.of(2026, 10, 14);
        assertEquals(java.time.LocalDate.of(2026, 10, 22), DateResolver.resolve("22 date as of now", today).orElseThrow());
    }

    @Test
    void inabilityToPayNowOrARealPromiseIsNotARefusal() {
        assertFalse(RefusalRules.isRefusal("Salary aane ke baad dunga, abhi paise nahi hain."));
        assertFalse(RefusalRules.isRefusal("Agle Friday tak 3000 de dunga."));
        assertFalse(RefusalRules.isRefusal("Kal 2000 UPI se bhej dunga"));
        assertFalse(RefusalRules.isRefusal(null));
    }

    @Test
    void sessionCountsRefusals() {
        var s = new CallSession("c");
        assertEquals(1, s.recordRefusal());
        assertEquals(2, s.recordRefusal());
    }
}
