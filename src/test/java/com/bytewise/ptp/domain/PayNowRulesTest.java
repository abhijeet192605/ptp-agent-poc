package com.bytewise.ptp.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayNowRulesTest {

    @Test
    void recognisesAPayNowRequestWithALink() {
        // The user's own wording
        var r = PayNowRules.detect("I will pay now send me payment link");
        assertTrue(r.requested());
        assertNull(r.channel());
    }

    @Test
    void recognisesPayNowInThreeLanguages() {
        assertTrue(PayNowRules.detect("Main abhi pay kar dunga").requested());
        assertTrue(PayNowRules.detect("Ata deto, link pathva").requested());
        assertTrue(PayNowRules.detect("Payment link bhejo").requested());
        assertTrue(PayNowRules.detect("I am paying right now").requested());
    }

    @Test
    void readsTheChannelTheCustomerAsksFor() {
        assertEquals(Channel.WHATSAPP, PayNowRules.detect("Send the payment link on WhatsApp").channel());
        assertEquals(Channel.WHATSAPP, PayNowRules.detect("Whatsapp pe link bhejo").channel());
        assertEquals(Channel.SMS, PayNowRules.detect("pay now, send the link by SMS").channel());
        assertNull(PayNowRules.detect("send the link on sms or whatsapp").channel());
    }

    @Test
    void noMoneyRightNowIsTheOppositeOfAPayNowRequest() {
        assertFalse(PayNowRules.detect("Abhi paise nahi hain").requested());
        assertFalse(PayNowRules.detect("I cannot pay now").requested());
        assertFalse(PayNowRules.detect("Do not send me any link").requested());
        assertFalse(PayNowRules.detect("Salary aane ke baad dunga, abhi nahi").requested());
    }

    @Test
    void ordinaryPromisesAreNotPayNowRequests() {
        assertFalse(PayNowRules.detect("Agle Friday tak 3000 de dunga.").requested());
        assertFalse(PayNowRules.detect("20 tarikh ko 4500 UPI se dunga").requested());
        assertFalse(PayNowRules.detect("Mujhe manager se baat karni hai").requested());
        assertFalse(PayNowRules.detect(null).requested());
        assertFalse(PayNowRules.detect("").requested());
    }

    @Test
    void numbersAreMaskedToTheLastFourDigits() {
        assertEquals("****0001", PhoneNumbers.mask("9876500001"));
        assertEquals("****0001", PhoneNumbers.mask("+91 98765 00001"));
        assertEquals("0001", PhoneNumbers.last4("9876500001"));
        assertEquals("", PhoneNumbers.last4(null));
        assertEquals("****12", PhoneNumbers.mask("12"));
    }
}
