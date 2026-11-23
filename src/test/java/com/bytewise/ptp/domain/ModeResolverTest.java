package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModeResolverTest {

    @Test
    void readsTheModeFromCustomerWords() {
        assertEquals("UPI", ModeResolver.fromText("Kal 2000 UPI se bhej dunga"));
        assertEquals("UPI", ModeResolver.fromText("PhonePe kar dunga"));
        assertEquals("CASH", ModeResolver.fromText("2500 rupaye cash deto"));
        assertEquals("CHEQUE", ModeResolver.fromText("purna amount cha cheque denar"));
        assertEquals("BANK_TRANSFER", ModeResolver.fromText("NEFT se bhejunga"));
    }

    @Test
    void noneOrAmbiguousGivesNull() {
        assertNull(ModeResolver.fromText("Agle Friday tak 3000 de dunga"));
        assertNull(ModeResolver.fromText("cash ya UPI, dekhta hoon"));
        assertNull(ModeResolver.fromText(null));
    }
}
