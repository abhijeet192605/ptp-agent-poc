package com.bytewise.ptp.domain;

/** Where a payment link stands. Part payments are accepted, so a link can be paid in pieces. */
public enum PaymentStatus {
    CREATED, SENT, PARTIALLY_PAID, PAID, EXPIRED, FAILED
}
