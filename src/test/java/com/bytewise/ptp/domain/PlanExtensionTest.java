package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExtensionTest {

    private final LocalDate today = LocalDate.of(2026, 10, 13); // Tuesday
    private final BigDecimal outstanding = new BigDecimal("25000.00");

    private PromisePlan onRecord(Installment... installments) {
        return new PromisePlan(List.of(installments), null);
    }

    private Installment inst(String amount, int day, String mode) {
        return new Installment(new BigDecimal(amount), LocalDate.of(2026, 10, day), mode);
    }

    @Test
    void theUsersExampleKeepsTheOldInstallmentAndAddsTheRemainder() {
        // Real test message: "Yes, but want give remaining amount on next week."
        var d = PlanExtension.extend(onRecord(inst("12500.00", 16, null)), outstanding,
                "Yes, but  want give remaining amount on next week.", today);
        assertTrue(d.accepted(), d.reasons().toString());
        var i = d.plan().installments();
        assertEquals(2, i.size());
        assertEquals(0, new BigDecimal("12500").compareTo(i.get(0).amount()));
        assertEquals(LocalDate.of(2026, 10, 16), i.get(0).date());
        assertEquals(0, new BigDecimal("12500").compareTo(i.get(1).amount())); // 25000 - 12500
        assertEquals(LocalDate.of(2026, 10, 20), i.get(1).date());            // next week
        assertEquals(0, outstanding.compareTo(d.plan().total()));
    }

    @Test
    void theRemainderTakesTheModeTheCustomerNames() {
        var d = PlanExtension.extend(onRecord(inst("10000", 16, "UPI")), outstanding,
                "baaki cash se 20 tarikh ko dunga", today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals("UPI", d.plan().installments().get(0).mode());
        assertEquals("CASH", d.plan().installments().get(1).mode());
        assertEquals(0, new BigDecimal("15000").compareTo(d.plan().installments().get(1).amount()));
    }

    @Test
    void withoutADateItAsksForTheDate() {
        var d = PlanExtension.extend(onRecord(inst("12500", 16, null)), outstanding, "I will pay the rest", today);
        assertFalse(d.accepted());
        assertTrue(d.reasons().contains("date is missing"), d.reasons().toString());
    }

    @Test
    void installmentsAlreadyInThePastAreNotCountedAsStillDue() {
        // 10 Oct has gone; only the 16 Oct installment still counts, so 25000 - 5000 = 20000 remains
        var d = PlanExtension.extend(onRecord(inst("9000", 10, null), inst("5000", 16, null)), outstanding,
                "remaining on 25 tarikh", today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(2, d.plan().installments().size());
        assertEquals(0, new BigDecimal("20000").compareTo(d.plan().installments().get(1).amount()));
    }

    @Test
    void nothingRemainsWhenThePromiseAlreadyCoversTheBalance() {
        var d = PlanExtension.extend(onRecord(inst("25000", 16, null)), outstanding, "the rest next week", today);
        assertFalse(d.accepted());
        assertTrue(d.reasons().get(0).contains("already covers"), d.reasons().toString());
    }

    @Test
    void aPromiseThatIsNoLongerDueCannotBeExtended() {
        var d = PlanExtension.extend(onRecord(inst("9000", 10, null)), outstanding, "the rest next week", today);
        assertFalse(d.accepted());
    }

    @Test
    void aDayOfTheMonthAlreadyPassedRollsToNextMonth() {
        var d = PlanExtension.extend(onRecord(inst("10000", 16, null)), outstanding, "the remaining on 5 tarikh", today);
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(LocalDate.of(2026, 11, 5), d.plan().installments().get(1).date());
    }

    @Test
    void recognisesTheRemainingInThreeLanguages() {
        assertTrue(PlanExtension.mentionsRemaining("I want to give the remaining amount next week"));
        assertTrue(PlanExtension.mentionsRemaining("Baaki amount agle hafte dunga"));
        assertTrue(PlanExtension.mentionsRemaining("Uravleli rakkam pudhchya aathvadyat deto"));
        assertTrue(PlanExtension.mentionsRemaining("Pay the rest on Friday"));
        assertFalse(PlanExtension.mentionsRemaining("Agle Friday tak 3000 de dunga."));
        assertFalse(PlanExtension.mentionsRemaining("I need to restructure my loan"));
        assertFalse(PlanExtension.mentionsRemaining(null));
    }
}
