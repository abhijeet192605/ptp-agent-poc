package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscalationRulesTest {

    @Test
    void escalatesOnRequestForAPersonOrLegalWording() {
        assertTrue(EscalationRules.requiresHuman("Mujhe manager se baat karni hai"));
        assertTrue(EscalationRules.requiresHuman("I will go to the Police"));
        assertTrue(EscalationRules.requiresHuman("This is harassment, stop calling me"));
    }

    @Test
    void ordinaryPromisesDoNotEscalate() {
        assertFalse(EscalationRules.requiresHuman("Agle Friday tak 3000 de dunga."));
        assertFalse(EscalationRules.requiresHuman("Salary aane ke baad dunga"));
        assertFalse(EscalationRules.requiresHuman(null));
    }
}
