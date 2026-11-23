package com.bytewise.ptp.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the customer promised on a call: one or more installments, plus anything the payment depends on.
 * A single payment is a plan with one installment.
 */
public record PromisePlan(List<Installment> installments, String conditions) {

    public PromisePlan {
        installments = List.copyOf(installments);
    }

    public Installment first() {
        return installments.get(0);
    }

    public BigDecimal total() {
        return installments.stream().map(Installment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
