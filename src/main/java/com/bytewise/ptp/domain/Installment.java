package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One payment the customer promised: how much, when, and how. */
public record Installment(BigDecimal amount, LocalDate date, String mode) {
}
