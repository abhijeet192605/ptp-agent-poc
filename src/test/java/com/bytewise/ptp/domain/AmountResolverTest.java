package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmountResolverTest {

    private final BigDecimal outstanding = new BigDecimal("10000.00");

    private Optional<BigDecimal> resolve(String text) {
        return AmountResolver.resolve(text, outstanding);
    }

    private void assertAmount(String expected, Optional<BigDecimal> actual) {
        assertTrue(actual.isPresent(), "expected an amount");
        assertEquals(0, new BigDecimal(expected).compareTo(actual.get()), actual.get().toPlainString());
    }

    @Test
    void percentages() {
        assertAmount("5000", resolve("I will pay 50% of the outstanding amount"));
        assertAmount("2500", resolve("25 percent dunga"));
        assertAmount("3000", resolve("30 takke deto"));
        assertAmount("1250", resolve("12.5% of it"));
        assertAmount("5000", resolve("50 pratishat"));
    }

    @Test
    void halfQuarterAndFull() {
        assertAmount("5000", resolve("half amount dunga"));
        assertAmount("5000", resolve("aadha amount de dunga"));
        assertAmount("2500", resolve("quarter dunga"));
        assertAmount("10000", resolve("full amount dunga"));
        assertAmount("10000", resolve("20 tarikh la purna amount cha cheque denar."));
        assertAmount("10000", resolve("poora dunga kal"));
    }

    @Test
    void roundsToPaise() {
        // 48500.50 x 33.33% = 16165.2167
        assertAmount("16165.22", AmountResolver.resolve("33.33% dunga", new BigDecimal("48500.50")));
    }

    @Test
    void datesAreNotMistakenForAmounts() {
        assertAmount("5000", resolve("50% by 20 tarikh"));
        assertAmount("5000", resolve("5 tarkhela aadha dunga"));
    }

    @Test
    void ambiguousOrImpossibleGivesNothing() {
        assertEquals(Optional.empty(), resolve("2000 abhi, baaki full baad mein")); // a rupee figure is named
        assertEquals(Optional.empty(), resolve("150% dunga"));
        assertEquals(Optional.empty(), resolve("half ya full dunga"));
        assertEquals(Optional.empty(), resolve("Agle Friday dunga"));
        assertEquals(Optional.empty(), AmountResolver.resolve("50%", null));
        assertEquals(Optional.empty(), AmountResolver.resolve("50%", BigDecimal.ZERO));
    }

    @Test
    void aQuestionAboutTheBalanceIsNotAPromise() {
        assertEquals(Optional.empty(), resolve("Mera outstanding kitna hai?"));
    }

    @Test
    void detectsRelativeAndStatedFigures() {
        assertTrue(AmountResolver.mentionsRelativeAmount("50% dunga"));
        assertTrue(AmountResolver.mentionsRelativeAmount("purna amount"));
        assertFalse(AmountResolver.mentionsRelativeAmount("3000 dunga"));
        assertTrue(AmountResolver.hasStatedNumber("3000 dunga"));
        assertFalse(AmountResolver.hasStatedNumber("20 tarikh la purna amount"));
        assertFalse(AmountResolver.hasStatedNumber("50% dunga"));
    }
}
