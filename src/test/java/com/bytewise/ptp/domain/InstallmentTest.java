package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Split payments: the parser and the gate's rules for them. */
class InstallmentTest {

    private final PromiseGate gate = new PromiseGate();
    private final LocalDate today = LocalDate.of(2026, 10, 13); // Tuesday
    private final BigDecimal outstanding = new BigDecimal("10000.00");

    private PromiseGate.Decision check(String text) {
        return gate.check(null, null, null, null, text, text, outstanding, today);
    }

    @Test
    void theUsersMarathiExampleBecomesTwoInstallments() {
        // Real test message from the user
        var d = check("20 tarikh la 50% amount cha cheque ne denar ani 50% amount 22 tarikh la CASH ne denar");
        assertTrue(d.accepted(), d.reasons().toString());
        var i = d.plan().installments();
        assertEquals(2, i.size());
        assertEquals(0, new BigDecimal("5000").compareTo(i.get(0).amount()));
        assertEquals(LocalDate.of(2026, 10, 20), i.get(0).date());
        assertEquals("CHEQUE", i.get(0).mode());
        assertEquals(0, new BigDecimal("5000").compareTo(i.get(1).amount()));
        assertEquals(LocalDate.of(2026, 10, 22), i.get(1).date());
        assertEquals("CASH", i.get(1).mode());
        assertEquals(0, outstanding.compareTo(d.plan().total()));
    }

    @Test
    void rupeeFiguresAndMixedWording() {
        var d = check("Kal 2000 UPI se bhej dunga aur parso 1500 cash dunga");
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(2, d.plan().installments().size());
        assertEquals(LocalDate.of(2026, 10, 14), d.plan().installments().get(0).date());
        assertEquals("UPI", d.plan().installments().get(0).mode());
        assertEquals(0, new BigDecimal("1500").compareTo(d.plan().installments().get(1).amount()));
    }

    @Test
    void installmentsAreSortedByDate() {
        var d = check("22 tarikh ko 3000 dunga ani 20 tarikh ko 2000 dunga");
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(LocalDate.of(2026, 10, 20), d.plan().installments().get(0).date());
        assertEquals(LocalDate.of(2026, 10, 22), d.plan().installments().get(1).date());
    }

    @Test
    void threeInstallmentsSeparatedByCommasAndWords() {
        var d = check("Friday 1000, next week 2000 and month end 3000");
        assertTrue(d.accepted(), d.reasons().toString());
        var i = d.plan().installments();
        assertEquals(3, i.size());
        assertEquals(LocalDate.of(2026, 10, 16), i.get(0).date());
        assertEquals(LocalDate.of(2026, 10, 20), i.get(1).date());
        assertEquals(LocalDate.of(2026, 10, 28), i.get(2).date());
        assertEquals(0, new BigDecimal("6000").compareTo(d.plan().total()));
    }

    @Test
    void theThirdInstallmentIsNeverSilentlyDropped() {
        // Real test message: "25 ko" was not read as a day, so the 3rd payment vanished and two were saved
        var text = "20 tarikh ko 3000 dunga aur 22 tarikh ko 5000 dunga and 5000 rupaye 25 ko dunga";
        var parsed = InstallmentParser.parse(text, outstanding, today);
        assertEquals(3, parsed.complete().size());
        // 3000 + 5000 + 5000 is above the 10000 balance, so the whole plan is refused
        var d = check(text);
        assertFalse(d.accepted());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("more than the outstanding")), d.reasons().toString());
    }

    @Test
    void threeInstallmentsWithADayWrittenAs25Ko() {
        var d = check("20 tarikh ko 3000 dunga aur 22 tarikh ko 2000 dunga and 1000 rupaye 25 ko dunga");
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(3, d.plan().installments().size());
        assertEquals(LocalDate.of(2026, 10, 25), d.plan().installments().get(2).date());
        assertEquals(0, new BigDecimal("1000").compareTo(d.plan().installments().get(2).amount()));
    }

    @Test
    void aPartWithNumbersWeCannotReadFailsThePlan() {
        var d = check("20 tarikh ko 3000 dunga aur 22 tarikh ko 2000 dunga aur 1000 1500 dunga");
        assertFalse(d.accepted());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("missing its amount or its date")), d.reasons().toString());
    }

    @Test
    void totalAboveTheBalanceIsRejected() {
        var d = check("20 tarikh ko 8000 dunga ani 22 tarikh ko 5000 dunga");
        assertFalse(d.accepted());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("more than the outstanding")), d.reasons().toString());
    }

    @Test
    void anInstallmentWithoutAnAmountIsRejected() {
        var d = check("20 tarikh ko 3000 dunga ani 22 tarikh ko 2000 dunga ani 25 tarikh ko");
        assertFalse(d.accepted());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("missing its amount or its date")), d.reasons().toString());
    }

    @Test
    void weekdaysAreResolvedPerInstallment() {
        var d = check("Friday 3000 dunga ani Monday 2000 dunga");
        assertTrue(d.accepted(), d.reasons().toString());
        assertEquals(LocalDate.of(2026, 10, 16), d.plan().installments().get(0).date());
        assertEquals(LocalDate.of(2026, 10, 19), d.plan().installments().get(1).date());
    }

    @Test
    void tooManyInstallmentsAreRejected() {
        var d = check("kal 100 ani parso 100 ani 16 tarikh ko 100 ani 17 tarikh ko 100 ani 18 tarikh ko 100 "
                + "ani 19 tarikh ko 100 ani 20 tarikh ko 100");
        assertFalse(d.accepted());
        assertTrue(d.reasons().stream().anyMatch(r -> r.contains("more than 6")), d.reasons().toString());
    }

    @Test
    void aSinglePromiseIsNotMistakenForASplit() {
        assertFalse(InstallmentParser.looksLikePlan("Agle Friday tak 3000 de dunga.", outstanding, today));
        assertFalse(InstallmentParser.looksLikePlan("Theek hai, 5 tarikh ko 2500 UPI se dunga.", outstanding, today));
        assertFalse(InstallmentParser.looksLikePlan("Main Friday ko aur 3000 dunga", outstanding, today));
        assertFalse(InstallmentParser.looksLikePlan(null, outstanding, today));
    }

    @Test
    void aSplitIsRecognised() {
        assertTrue(InstallmentParser.looksLikePlan(
                "20 tarikh la 50% amount cha cheque ne denar ani 50% amount 22 tarikh la CASH ne denar", outstanding, today));
        assertTrue(InstallmentParser.looksLikePlan("Kal 2000 aur parso 1500", null, today));
    }

    @Test
    void statedAmountIgnoresPercentagesAndDays() {
        assertEquals(Optional.of(new BigDecimal("2000")), AmountResolver.statedAmount("22 tarikh ko 2000 dunga"));
        assertEquals(Optional.empty(), AmountResolver.statedAmount("50% on 20 tarikh"));
        assertEquals(Optional.empty(), AmountResolver.statedAmount("2000 ya 3000"));
    }

    @Test
    void confirmationListsEveryInstallment() {
        var plan = new PromisePlan(java.util.List.of(
                new Installment(new BigDecimal("5000.00"), LocalDate.of(2026, 10, 20), "CHEQUE"),
                new Installment(new BigDecimal("5000.00"), LocalDate.of(2026, 10, 22), "CASH")), null);
        String en = Replies.confirm(plan, Language.ENGLISH);
        assertEquals("Thank you. I have noted your promises: Rs 5000 on 20 Oct 2026 (cheque); "
                + "Rs 5000 on 22 Oct 2026 (cash).", en);
        assertTrue(Replies.confirm(plan, Language.MARATHI).contains("Rs 5000 on 22 Oct 2026 (cash)"));
    }
}
