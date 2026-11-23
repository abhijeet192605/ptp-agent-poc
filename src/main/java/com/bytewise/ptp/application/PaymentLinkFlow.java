package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.AmountResolver;
import com.bytewise.ptp.domain.CallSession;
import com.bytewise.ptp.domain.Channel;
import com.bytewise.ptp.domain.Installment;
import com.bytewise.ptp.domain.InstallmentParser;
import com.bytewise.ptp.domain.Language;
import com.bytewise.ptp.domain.LoanAccount;
import com.bytewise.ptp.domain.PaymentLink;
import com.bytewise.ptp.domain.PaymentStatus;
import com.bytewise.ptp.domain.PhoneNumbers;
import com.bytewise.ptp.domain.PromisePlan;
import com.bytewise.ptp.domain.Replies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * "I will pay now, send me the payment link": keeps today's promise, creates a link for the full outstanding balance
 * (the customer may pay all of it or a part), and sends it to the mobile number on record. Everything here is code:
 * the model never sees the link, the number or the amount.
 */
@Component
public class PaymentLinkFlow {

    private static final Logger log = LoggerFactory.getLogger(PaymentLinkFlow.class);

    static final Duration LINK_VALIDITY = Duration.ofHours(24);

    public enum Kind { SENT, SEND_FAILED, NO_MOBILE, NOTHING_DUE, AMOUNT_TOO_HIGH }

    /** What was sent, shown by the test page. The number is masked. */
    public record Notification(Channel channel, String to, String text) {
    }

    /** @param plan the promise kept for today, or null when nothing was saved */
    public record Result(Kind kind, String reply, PromisePlan plan, Notification notification) {
    }

    private final LoanAccounts accounts;
    private final PaymentGateway gateway;
    private final MessageSender sender;
    private final PaymentLinkStore links;
    private final OutboundMessages outbound;
    private final PromiseStore promises;
    private final Clock clock;

    public PaymentLinkFlow(LoanAccounts accounts, PaymentGateway gateway, MessageSender sender,
                           PaymentLinkStore links, OutboundMessages outbound, PromiseStore promises, Clock clock) {
        this.accounts = accounts;
        this.gateway = gateway;
        this.sender = sender;
        this.links = links;
        this.outbound = outbound;
        this.promises = promises;
        this.clock = clock;
    }

    public Result handle(CallSession session, String message, Channel requested, Language lang, LocalDate today) {
        LoanAccount account = session.accountId() == null ? null : accounts.find(session.accountId()).orElse(null);
        if (account == null || !account.hasMobile()) {
            return new Result(Kind.NO_MOBILE, Replies.noMobile(lang), null, null);
        }
        BigDecimal outstanding = account.outstanding();
        if (outstanding.signum() <= 0) {
            return new Result(Kind.NOTHING_DUE, Replies.nothingDue(lang), null, null);
        }

        // The promise is the amount the customer named, or the full outstanding. A long digit string is a phone
        // number the customer read out, not an amount.
        String forAmount = message.replaceAll("\\d{8,}", " ");
        BigDecimal amount = AmountResolver.resolve(forAmount, outstanding)
                .or(() -> AmountResolver.statedAmount(forAmount))
                .orElse(outstanding);
        if (amount.signum() <= 0 || amount.compareTo(outstanding) > 0) {
            return new Result(Kind.AMOUNT_TOO_HIGH, Replies.amountTooHigh(outstanding, lang), null, null);
        }

        // WhatsApp only where the customer agreed to it; otherwise SMS.
        Channel channel = requested != null ? requested : (account.whatsappOptIn() ? Channel.WHATSAPP : Channel.SMS);
        if (channel == Channel.WHATSAPP && !account.whatsappOptIn()) {
            channel = Channel.SMS;
        }

        // Today's promise is kept first, so it stands even if the link cannot be made or sent.
        PromisePlan plan = new PromisePlan(List.of(new Installment(amount, today, "PAYMENT_LINK")), null);
        PromisePlan withLater = planWithLaterInstallments(forAmount, outstanding, today);
        if (withLater != null) {
            plan = withLater; // "5000 today by link and 5000 tomorrow": the later part is saved too
        }
        promises.save(session.callId(), plan);

        String masked = PhoneNumbers.mask(account.mobile());
        PaymentGateway.GatewayLink gatewayLink;
        try {
            Instant expires = clock.instant().plus(LINK_VALIDITY);
            // Part payment is accepted: the link is for the whole balance and the customer pays what they can.
            gatewayLink = gateway.createLink(account.accountId(), outstanding, true, expires);
            links.save(new PaymentLink(gatewayLink.providerRef(), session.callId(), account.accountId(), outstanding,
                    true, gatewayLink.url(), channel, masked, PaymentStatus.CREATED, BigDecimal.ZERO, expires));
        } catch (RuntimeException e) {
            log.warn("[{}] could not create the payment link: {}", session.callId(), e.toString());
            return new Result(Kind.SEND_FAILED, Replies.linkFailed(lang), plan, null);
        }

        String body = Replies.paymentLinkMessage(account.customerName(), outstanding, gatewayLink.url(), lang);
        boolean ok;
        try {
            ok = sender.send(channel, account.mobile(), body).ok();
        } catch (RuntimeException e) {
            log.warn("[{}] could not send the payment link: {}", session.callId(), e.toString());
            ok = false;
        }
        outbound.record(session.callId(), channel, masked, body, ok);
        links.updateStatus(gatewayLink.providerRef(), ok ? PaymentStatus.SENT : PaymentStatus.FAILED);
        log.debug("[{}] payment link {} for Rs {} sent={} via {} to {}", session.callId(), gatewayLink.providerRef(),
                outstanding, ok, channel, masked);

        if (!ok) {
            return new Result(Kind.SEND_FAILED, Replies.linkFailed(lang), plan, null);
        }
        String reply = Replies.linkSent(channel, PhoneNumbers.last4(account.mobile()), outstanding, lang);
        if (plan.installments().size() > 1) {
            reply += Replies.alsoNoted(plan, lang);
        }
        return new Result(Kind.SENT, reply, plan, new Notification(channel, masked, body));
    }

    /**
     * When the message is a split that has exactly one part for today and every other part later, within the
     * balance, returns the whole plan with today's part paid by link; otherwise null.
     */
    private static PromisePlan planWithLaterInstallments(String message, BigDecimal outstanding, LocalDate today) {
        var parsed = InstallmentParser.parse(message, outstanding, today);
        if (!parsed.isPlan() || !parsed.partial().isEmpty()) {
            return null;
        }
        var parts = parsed.complete();
        long todays = parts.stream().filter(s -> s.date().equals(today)).count();
        BigDecimal total = parts.stream().map(InstallmentParser.Segment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (todays != 1 || total.compareTo(outstanding) > 0 || parts.stream().anyMatch(s -> s.date().isBefore(today))) {
            return null;
        }
        List<Installment> all = new java.util.ArrayList<>();
        for (var s : parts) {
            all.add(new Installment(s.amount(), s.date(), s.date().equals(today) ? "PAYMENT_LINK" : s.mode()));
        }
        all.sort(java.util.Comparator.comparing(Installment::date));
        return new PromisePlan(all, null);
    }
}
