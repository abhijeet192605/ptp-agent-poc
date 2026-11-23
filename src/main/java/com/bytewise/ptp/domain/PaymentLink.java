package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment link made for one call. The amount is the full outstanding balance at the time, and the customer may
 * pay all of it or any part of it.
 */
public record PaymentLink(String linkId, String callId, String accountId, BigDecimal amount, boolean acceptPartial,
                          String url, Channel channel, String destinationMasked, PaymentStatus status,
                          BigDecimal paidAmount, Instant expiresAt) {

    public BigDecimal remaining() {
        return amount.subtract(paidAmount).max(BigDecimal.ZERO);
    }

    public PaymentLink withStatus(PaymentStatus newStatus) {
        return new PaymentLink(linkId, callId, accountId, amount, acceptPartial, url, channel, destinationMasked,
                newStatus, paidAmount, expiresAt);
    }
}
