package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageAndRepliesTest {

    @Test
    void detectsLanguages() {
        assertEquals(Language.ENGLISH, Language.detect("I will pay 3000 by Friday"));
        assertEquals(Language.HINGLISH, Language.detect("Agle Friday tak 3000 de dunga."));
        assertEquals(Language.HINGLISH, Language.detect("Kal 2000 UPI se bhej dunga."));
        assertEquals(Language.MARATHI, Language.detect("Saheb, pagar 5 tarkhela honar, tevha 2500 rupaye cash deto."));
        assertEquals(Language.MARATHI, Language.detect("Mi shanivar 3500 rupaye bank transfer karto."));
        // Real test message: both "tarikh" (Hindi list) and several Marathi words
        assertEquals(Language.MARATHI, Language.detect("20 tarikh la purna amount cha cheque denar."));
        assertEquals(Language.MARATHI, Language.detect("Salary honar tevha denar, 5 tarkhela"));
    }

    @Test
    void openingQuotesTheOutstandingBalanceInEveryLanguage() {
        for (Language l : Language.values()) {
            String s = Replies.opening("Ramesh", new BigDecimal("10000.00"), l);
            assertTrue(s.contains("Rs 10000") && s.contains("Ramesh"), s);
        }
    }

    @Test
    void englishMessageDoesNotOverrideAnEarlierLanguage() {
        var s = new CallSession("c");
        s.observeLanguage(Language.detect("Salary aane ke baad dunga"));
        s.observeLanguage(Language.detect("Friday"));
        assertEquals(Language.HINGLISH, s.language());
    }

    @Test
    void confirmationCarriesTheExactAmountAndDate() {
        String en = Replies.confirm(new BigDecimal("3000.00"), LocalDate.of(2026, 10, 16), Language.ENGLISH);
        assertEquals("Thank you. I have noted your promise of Rs 3000 on 16 Oct 2026.", en);
        assertTrue(Replies.confirm(new BigDecimal("2500"), LocalDate.of(2026, 11, 5), Language.MARATHI)
                .contains("Rs 2500"));
    }

    @Test
    void paymentLinkWordingCarriesTheFactsInEveryLanguage() {
        for (Language l : Language.values()) {
            String message = Replies.paymentLinkMessage("Ramesh", new BigDecimal("10000.00"),
                    "https://pay.example.test/l/abc", l);
            assertTrue(message.contains("https://pay.example.test/l/abc") && message.contains("Rs 10000"), message);

            String sent = Replies.linkSent(Channel.WHATSAPP, "0001", new BigDecimal("10000.00"), l);
            assertTrue(sent.contains("WhatsApp") && sent.contains("0001") && sent.contains("Rs 10000"), sent);
            assertFalse(sent.contains("http"), "the link is in the message, never read out");

            assertFalse(Replies.linkFailed(l).isBlank());
            assertFalse(Replies.noMobile(l).isBlank());
            assertFalse(Replies.nothingDue(l).isBlank());
            assertTrue(Replies.amountTooHigh(new BigDecimal("10000"), l).contains("Rs 10000"));
        }
        assertTrue(Replies.linkSent(Channel.SMS, "0002", BigDecimal.TEN, Language.ENGLISH).contains("SMS"));
    }

    @Test
    void everyLanguageHasEveryReply() {
        for (Language l : Language.values()) {
            for (Replies.Ask a : Replies.Ask.values()) {
                assertTrue(!Replies.ask(a, l).isBlank());
            }
            assertTrue(!Replies.escalated(l).isBlank());
            assertTrue(!Replies.ended(l).isBlank());
        }
    }
}
