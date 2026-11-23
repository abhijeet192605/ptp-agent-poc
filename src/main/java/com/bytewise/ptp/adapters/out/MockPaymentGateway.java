package com.bytewise.ptp.adapters.out;

import com.bytewise.ptp.application.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stand-in for a payment provider such as Razorpay or Cashfree. It makes a believable link and nothing else.
 * Replace it with an adapter that calls the provider's API, and verify the provider's webhook signature.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public GatewayLink createLink(String accountId, BigDecimal amount, boolean acceptPartial, Instant expiresAt) {
        String ref = "plink_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String url = "https://pay.example.test/l/" + ref;
        log.debug("[mock gateway] link {} for account {} amount {} partial={} until {}",
                ref, accountId, amount, acceptPartial, expiresAt);
        return new GatewayLink(ref, url);
    }
}
