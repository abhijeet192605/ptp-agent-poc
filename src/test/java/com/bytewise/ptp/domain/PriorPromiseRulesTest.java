package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriorPromiseRulesTest {

    private final PromisePlan plan = new PromisePlan(List.of(
            new Installment(new BigDecimal("12500.00"), LocalDate.of(2026, 10, 16), "UPI")), null);

    @Test
    void recognisesACustomerWhoSaysTheyAlreadyGaveAPromise() {
        // Real test message from the user
        assertTrue(PriorPromiseRules.mentionsEarlierPromise("Maine already date apko bata di thi in last call."));
        assertTrue(PriorPromiseRules.mentionsEarlierPromise("I told you in the previous call"));
        assertTrue(PriorPromiseRules.mentionsEarlierPromise("Mi aadhich sangitle aahe"));
    }

    @Test
    void ordinaryPromisesAreNotMistakenForAReference() {
        assertFalse(PriorPromiseRules.mentionsEarlierPromise("Agle Friday tak 3000 de dunga."));
        assertFalse(PriorPromiseRules.mentionsEarlierPromise("Pehle 1000 dunga, baaki baad mein"));
        assertFalse(PriorPromiseRules.mentionsEarlierPromise(null));
    }

    @Test
    void aPlainYesIsRecognisedInThreeLanguages() {
        assertTrue(PriorPromiseRules.isPlainYes("Yes"));
        assertTrue(PriorPromiseRules.isPlainYes("Yes, that is correct"));
        assertTrue(PriorPromiseRules.isPlainYes("Haan"));
        assertTrue(PriorPromiseRules.isPlainYes("Theek hai"));
        assertTrue(PriorPromiseRules.isPlainYes("Ho, barobar aahe"));
    }

    @Test
    void aYesThatCarriesAnythingElseIsNotAPlainYes() {
        assertFalse(PriorPromiseRules.isPlainYes("Haan but I want to change it"));
        assertFalse(PriorPromiseRules.isPlainYes("No"));
        assertFalse(PriorPromiseRules.isPlainYes("Nahi, badalna hai"));
        assertFalse(PriorPromiseRules.isPlainYes("ok fine 2000 kal"));
        assertFalse(PriorPromiseRules.isPlainYes("yes I will pay it in two installments of equal size next month"));
        assertFalse(PriorPromiseRules.isPlainYes(""));
        assertFalse(PriorPromiseRules.isPlainYes(null));
    }

    @Test
    void wordingQuotesTheRecordedPromiseInEveryLanguage() {
        for (Language l : Language.values()) {
            String opening = Replies.openingWithPromise("Sunita", new BigDecimal("25000.00"), plan, l);
            String existing = Replies.existingPromise(plan, l);
            String kept = Replies.keptExisting(plan, l);
            for (String s : List.of(opening, existing, kept)) {
                assertTrue(s.contains("Rs 12500 on 16 Oct 2026 (UPI)"), s);
            }
            assertTrue(opening.contains("Rs 25000") && opening.contains("Sunita"), opening);
        }
    }
}
