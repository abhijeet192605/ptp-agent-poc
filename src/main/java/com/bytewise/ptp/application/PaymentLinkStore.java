package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.PaymentLink;
import com.bytewise.ptp.domain.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Storage for payment links and the payments made against them. */
public interface PaymentLinkStore {

    /** The link after a payment, and the account's outstanding balance now. */
    record PaymentApplied(PaymentLink link, BigDecimal accountOutstanding) {
    }

    void save(PaymentLink link);

    void updateStatus(String linkId, PaymentStatus status);

    Optional<PaymentLink> findByLinkId(String linkId);

    /** The newest link made on this call. */
    Optional<PaymentLink> findByCall(String callId);

    /** Newest first. */
    List<PaymentLink> all();

    /**
     * Records a payment against a link, as a provider's webhook would, and lowers the account's outstanding balance.
     *
     * @throws IllegalArgumentException if the link is unknown, closed, or the payment is not positive or is more than
     *                                  what is left on the link
     */
    PaymentApplied applyPayment(String linkId, BigDecimal amount);
}
