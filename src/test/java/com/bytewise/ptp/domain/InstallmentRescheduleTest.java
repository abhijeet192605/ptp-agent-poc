package com.bytewise.ptp.domain;

import com.bytewise.ptp.domain.InstallmentReschedule.Kind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallmentRescheduleTest {

    private final LocalDate today = LocalDate.of(2026, 10, 14); // Wednesday

    private Installment inst(String amount, int day, String mode) {
        return new Installment(new BigDecimal(amount), LocalDate.of(2026, 10, day), mode);
    }

    /** The plan from the real test: 12,500 on 16 Oct and 12,500 on 21 Oct. */
    private PromisePlan twoInstallments() {
        return new PromisePlan(List.of(inst("12500.00", 16, null), inst("12500.00", 21, null)), null);
    }

    @Test
    void theUsersSentenceMovesTheSecondInstallmentToThe23rd() {
        // Real voice test: the agent asked for the amount and date again instead of doing this
        var out = InstallmentReschedule.apply(twoInstallments(),
                "so my next installment will be delayed by 2 days that is on 23rd I will pay the next installment", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        var i = out.plan().installments();
        assertEquals(2, i.size());
        assertEquals(LocalDate.of(2026, 10, 16), i.get(0).date());   // the first stays
        assertEquals(LocalDate.of(2026, 10, 23), i.get(1).date());   // 21 + 2 days
        assertEquals(0, new BigDecimal("12500").compareTo(i.get(1).amount())); // the amount is never asked again
    }

    /** The second real test: Rs 12,125.13 on 14 Oct by UPI, and the customer needs it a day later. */
    private PromisePlan oneInstallment() {
        return new PromisePlan(List.of(new Installment(new BigDecimal("12125.13"), LocalDate.of(2026, 10, 14), "UPI")), null);
    }

    @Test
    void oneMoreAdditionalDayMovesTheOnlyInstallmentByOneDay() {
        var out = InstallmentReschedule.apply(oneInstallment(),
                "no I need one more additional day to pay this amount can you please update in your record", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        assertEquals(LocalDate.of(2026, 10, 15), out.plan().first().date());
        assertEquals(0, new BigDecimal("12125.13").compareTo(out.plan().first().amount()));
        assertEquals("UPI", out.plan().first().mode());
    }

    @Test
    void nextDayRatherThanThe14thIsRecognised() {
        // the amount was garbled by speech recognition ("12125 13"): it is ignored, the record has the amount
        var out = InstallmentReschedule.apply(oneInstallment(),
                "I already told that 12125 13 I am going to pay next day rather than 14", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        assertEquals(LocalDate.of(2026, 10, 15), out.plan().first().date());
    }

    @Test
    void aBareDateAfterThePromiseWasShownMovesItToThatDate() {
        assertTrue(InstallmentReschedule.isNewDateOnly("I am going to pay on 15th", today));
        var out = InstallmentReschedule.apply(oneInstallment(), "I am going to pay on 15th", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        assertEquals(LocalDate.of(2026, 10, 15), out.plan().first().date());
    }

    @Test
    void aDateWithAnAmountIsANewPromiseNotAMove() {
        assertFalse(InstallmentReschedule.isNewDateOnly("I will pay 3000 on 15th", today));
        assertFalse(InstallmentReschedule.isNewDateOnly("I will pay half on 15th", today));
        assertFalse(InstallmentReschedule.isNewDateOnly("I will think about it", today));
    }

    @Test
    void ratherThanPicksTheRightInstallmentOutOfTwo() {
        var out = InstallmentReschedule.apply(twoInstallments(), "I will pay on 25th instead of 21", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        assertEquals(LocalDate.of(2026, 10, 16), out.plan().installments().get(0).date());
        assertEquals(LocalDate.of(2026, 10, 25), out.plan().installments().get(1).date());
        // with two installments, a bare date does not say which one
        assertEquals(Kind.ASK_WHICH, InstallmentReschedule.apply(twoInstallments(), "I am going to pay on 25th", today).kind());
    }

    @Test
    void extraDayWordingVariants() {
        for (String said : new String[]{"I need an extra day", "give me another day", "one day later please",
                "can I get 1 more day", "a day more to pay"}) {
            assertTrue(InstallmentReschedule.mentionsReschedule(said), said);
            var out = InstallmentReschedule.apply(oneInstallment(), said, today);
            assertEquals(LocalDate.of(2026, 10, 15), out.plan().first().date(), said);
        }
        var week = InstallmentReschedule.apply(oneInstallment(), "I need two more weeks", today);
        assertEquals(LocalDate.of(2026, 10, 28), week.plan().first().date());
    }

    @Test
    void aDelayAloneWorksWhenOnlyOneInstallmentIsLeft() {
        var plan = new PromisePlan(List.of(inst("5000", 20, "UPI")), null);
        var out = InstallmentReschedule.apply(plan, "I need to delay the payment by a week", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        assertEquals(LocalDate.of(2026, 10, 27), out.plan().first().date());
        assertEquals("UPI", out.plan().first().mode());
    }

    @Test
    void anOrdinalSaysWhichInstallmentToMove() {
        var out = InstallmentReschedule.apply(twoInstallments(), "push the second payment by 3 days", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        assertEquals(LocalDate.of(2026, 10, 24), out.plan().installments().get(1).date());
        assertEquals(LocalDate.of(2026, 10, 16), out.plan().installments().get(0).date());
    }

    @Test
    void theFirstOrTheLastCanBeNamed() {
        var first = InstallmentReschedule.apply(twoInstallments(), "move the first installment to the 18th", today);
        assertEquals(LocalDate.of(2026, 10, 18), first.plan().installments().get(0).date());
        var last = InstallmentReschedule.apply(twoInstallments(), "postpone the last one to 25th", today);
        assertEquals(LocalDate.of(2026, 10, 25), last.plan().installments().get(1).date());
    }

    @Test
    void movingPastTheOtherInstallmentKeepsTheScheduleInOrder() {
        var out = InstallmentReschedule.apply(twoInstallments(), "delay the first installment to the 24th", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        assertEquals(LocalDate.of(2026, 10, 21), out.plan().installments().get(0).date());
        assertEquals(LocalDate.of(2026, 10, 24), out.plan().installments().get(1).date());
    }

    @Test
    void whenItIsNotClearWhichInstallmentItAsks() {
        assertEquals(Kind.ASK_WHICH, InstallmentReschedule.apply(twoInstallments(), "delay it by 2 days", today).kind());
        assertEquals(Kind.ASK_WHICH, InstallmentReschedule.apply(twoInstallments(), "move it to the 25th", today).kind());
        // "delayed by 3 days, that is the 23rd" matches no installment: 16+3 and 21+3 are not the 23rd
        assertEquals(Kind.ASK_WHICH,
                InstallmentReschedule.apply(twoInstallments(), "delayed by 3 days that is on the 23rd", today).kind());
    }

    @Test
    void withNoNewDateItAsksForTheDate() {
        var plan = new PromisePlan(List.of(inst("5000", 20, null)), null);
        assertEquals(Kind.ASK_NEW_DATE, InstallmentReschedule.apply(plan, "I want to postpone my payment", today).kind());
    }

    @Test
    void aDayOfTheMonthThatHasPassedMeansNextMonth() {
        // "the 10th" cannot mean 10 October, which is behind us, so it is 10 November: allowed, and a real move
        var plan = new PromisePlan(List.of(inst("5000", 20, null)), null);
        var out = InstallmentReschedule.apply(plan, "move the payment to the 10th", today);
        assertEquals(Kind.RESCHEDULED, out.kind());
        assertEquals(LocalDate.of(2026, 11, 10), out.plan().first().date());
    }

    @Test
    void movingToTheDateItIsAlreadyOnChangesNothingSoItAsks() {
        var plan = new PromisePlan(List.of(inst("5000", 20, null)), null);
        assertEquals(Kind.ASK_NEW_DATE, InstallmentReschedule.apply(plan, "move the payment to the 20th", today).kind());
    }

    @Test
    void installmentsAlreadyInThePastCannotBeMoved() {
        var plan = new PromisePlan(List.of(inst("5000", 10, null)), null);
        assertEquals(Kind.NOT_POSSIBLE, InstallmentReschedule.apply(plan, "delay it by 2 days", today).kind());
    }

    @Test
    void recognisesTalkAboutMovingAnInstallment() {
        assertTrue(InstallmentReschedule.mentionsReschedule(
                "so my next installment will be delayed by 2 days that is on 23rd"));
        assertTrue(InstallmentReschedule.mentionsReschedule("Can I postpone the second payment"));
        assertTrue(InstallmentReschedule.mentionsReschedule("pehli kist 3 din aage badha do"));
        assertFalse(InstallmentReschedule.mentionsReschedule("Agle Friday tak 3000 de dunga."));
        assertFalse(InstallmentReschedule.mentionsReschedule("I will pay the remaining amount next week"));
        assertFalse(InstallmentReschedule.mentionsReschedule("I moved to a new house"));
        assertFalse(InstallmentReschedule.mentionsReschedule(null));
    }

    @Test
    void partiallyCorrectIsRecognisedAndAPlainYesIsNot() {
        assertTrue(PriorPromiseRules.isPartialAgreement("it's partially correct"));
        assertTrue(PriorPromiseRules.isPartialAgreement("Not exactly, some of it changed"));
        assertFalse(PriorPromiseRules.isPartialAgreement("Yes that is correct"));
        assertFalse(PriorPromiseRules.isPartialAgreement(null));
    }

    @Test
    void theAskWordingQuotesTheRecordInEveryLanguage() {
        for (Language l : Language.values()) {
            assertTrue(Replies.askWhichInstallment(twoInstallments(), l).contains("Rs 12500 on 16 Oct 2026"));
            assertTrue(Replies.askWhatChanges(twoInstallments(), l).contains("Rs 12500 on 21 Oct 2026"));
        }
    }
}
