package com.bytewise.ptp.application;

import java.math.BigDecimal;
import java.time.Instant;

/** Creates payment links at a payment provider. The POC uses a mock; a real provider is an adapter behind this port. */
public interface PaymentGateway {

    /** @param providerRef the provider's id for the link, which its payment webhooks refer to */
    record GatewayLink(String providerRef, String url) {
    }

    /**
     * @param acceptPartial whether the customer may pay less than the full amount
     * @throws RuntimeException if the provider cannot create the link
     */
    GatewayLink createLink(String accountId, BigDecimal amount, boolean acceptPartial, Instant expiresAt);
}
