package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FollowUpTest {

    private final LocalDate today = LocalDate.of(2026, 10, 13);
    private final LocalDate date = LocalDate.of(2026, 10, 20);

    @Test
    void implausibleDateCountsAsMissing() {
        // Real model output for "Shaadi ke baad dunga 2000": it invented 2051-01-01
        var p = new PtpExtraction(true, new BigDecimal("2000"), LocalDate.of(2051, 1, 1), null, "after the wedding");
        assertEquals(Optional.of(FollowUp.ASK_DATE), FollowUp.questionFor(p, today));
    }

    @Test
    void conditionalNonPromiseAsksForDateAndAmount() {
        // "Salary aane ke baad dunga, abhi paise nahi hain"
        var p = new PtpExtraction(false, null, null, null, "after receiving salary");
        assertEquals(Optional.of(FollowUp.ASK_BOTH), FollowUp.questionFor(p, today));
    }

    @Test
    void completePromiseNeedsNoFollowUp() {
        var p = new PtpExtraction(true, new BigDecimal("4500"), date, "UPI", null);
        assertEquals(Optional.empty(), FollowUp.questionFor(p, today));
    }

    @Test
    void missingDateAsksForDate() {
        var p = new PtpExtraction(true, new BigDecimal("2000"), null, null, "after the wedding");
        assertEquals(Optional.of(FollowUp.ASK_DATE), FollowUp.questionFor(p, today));
    }

    @Test
    void missingAmountAsksForAmount() {
        var p = new PtpExtraction(true, null, date, null, null);
        assertEquals(Optional.of(FollowUp.ASK_AMOUNT), FollowUp.questionFor(p, today));
    }

    @Test
    void zeroAmountCountsAsMissing() {
        var p = new PtpExtraction(true, BigDecimal.ZERO, date, null, null);
        assertEquals(Optional.of(FollowUp.ASK_AMOUNT), FollowUp.questionFor(p, today));
    }
}

