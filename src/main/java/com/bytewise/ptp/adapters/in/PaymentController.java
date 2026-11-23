package com.bytewise.ptp.adapters.in;

import com.bytewise.ptp.application.OutboundMessages;
import com.bytewise.ptp.application.PaymentLinkStore;
import com.bytewise.ptp.application.PromiseStore;
import com.bytewise.ptp.domain.PaymentLink;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Payment links, the messages that carried them, and a stand-in for the payment provider's webhook. A real provider
 * calls a webhook like {@code mock-payment} when a customer pays; it must verify the provider's signature first.
 */
@RestController
public class PaymentController {

    /** @param promisedAmount what the customer promised for today, and whether what was paid covers it */
    public record PaymentView(PaymentLink link, BigDecimal promisedAmount, boolean fulfilsPromise) {
    }

    /** A payment made by the customer, as the provider would report it. */
    public record MockPaymentRequest(BigDecimal amount) {
    }

    public record MockPaymentResult(PaymentView payment, BigDecimal accountOutstanding) {
    }

    private final PaymentLinkStore links;
    private final OutboundMessages outbound;
    private final PromiseStore promises;

    public PaymentController(PaymentLinkStore links, OutboundMessages outbound, PromiseStore promises) {
        this.links = links;
        this.outbound = outbound;
        this.promises = promises;
    }

    /** Every payment link, newest first, with whether what was paid covers the promise for today. */
    @GetMapping("/payments")
    public List<PaymentView> payments() {
        return links.all().stream().map(this::view).toList();
    }

    /** The messages the agent sent, newest first. */
    @GetMapping("/messages")
    public List<OutboundMessages.Entry> messages() {
        return outbound.recent(50);
    }

    /**
     * Simulates the customer paying part or all of a link. The paid amount comes off the account's outstanding balance.
     * A part payment is fine: paying 5000 on a 10000 link leaves the link PARTIALLY_PAID and 5000 outstanding.
     */
    @PostMapping("/payments/{linkId}/mock-payment")
    public MockPaymentResult mockPayment(@PathVariable String linkId, @RequestBody MockPaymentRequest request) {
        var applied = links.applyPayment(linkId, request.amount());
        return new MockPaymentResult(view(applied.link()), applied.accountOutstanding());
    }

    private PaymentView view(PaymentLink link) {
        BigDecimal promised = promises.find(link.callId()).map(plan -> plan.total()).orElse(null);
        boolean fulfils = promised != null && link.paidAmount().compareTo(promised) >= 0;
        return new PaymentView(link, promised, fulfils);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
