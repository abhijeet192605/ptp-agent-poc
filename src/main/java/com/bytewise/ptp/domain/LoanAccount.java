package com.bytewise.ptp.domain;

import java.math.BigDecimal;

/**
 * The part of the customer's loan the agent needs on a call. The mobile number is the one on record: a payment link
 * only ever goes there, never to a number given on the call.
 */
public record LoanAccount(String accountId, String customerName, BigDecimal outstanding, String mobile,
                          boolean whatsappOptIn) {

    public LoanAccount(String accountId, String customerName, BigDecimal outstanding) {
        this(accountId, customerName, outstanding, null, false);
    }

    public boolean hasMobile() {
        return mobile != null && !mobile.isBlank();
    }
}
