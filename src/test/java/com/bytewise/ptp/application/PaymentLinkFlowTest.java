package com.bytewise.ptp.application;

import com.bytewise.ptp.application.PaymentLinkFlow.Kind;
import com.bytewise.ptp.domain.CallSession;
import com.bytewise.ptp.domain.Channel;
import com.bytewise.ptp.domain.Language;
import com.bytewise.ptp.domain.LoanAccount;
import com.bytewise.ptp.domain.PaymentLink;
import com.bytewise.ptp.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "I will pay now, send me the link", with a fake gateway and a fake sender: nothing leaves the machine. */
class PaymentLinkFlowTest {

    private final LocalDate today = LocalDate.of(2026, 10, 14);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);

    // ---- fakes -------------------------------------------------------------
    private final List<String> gatewayCalls = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();
    private final List<PaymentLink> savedLinks = new ArrayList<>();
    private final List<OutboundMessages.Entry> audit = new ArrayList<>();
    private final InMemoryPromiseStore promises = new InMemoryPromiseStore();
    private boolean gatewayFails;
    private boolean senderOk = true;
    private Map<String, LoanAccount> accountMap;

    private PaymentLinkFlow flow;
    private CallSession session;

    @BeforeEach
    void setUp() {
        accountMap = Map.of(
                "ACC1", new LoanAccount("ACC1", "Ramesh Patil", new BigDecimal("10000.00"), "9876500001", true),
                "ACC3", new LoanAccount("ACC3", "Amit Verma", new BigDecimal("48500.50"), "9876500003", false),
                "NOMOB", new LoanAccount("NOMOB", "No Mobile", new BigDecimal("5000.00"), null, false),
                "PAID", new LoanAccount("PAID", "All Paid", BigDecimal.ZERO, "9876500009", true));

        LoanAccounts accounts = new LoanAccounts() {
            public Optional<LoanAccount> find(String id) { return Optional.ofNullable(accountMap.get(id)); }
            public List<LoanAccount> all() { return List.copyOf(accountMap.values()); }
        };
        PaymentGateway gateway = (accountId, amount, partial, expires) -> {
            if (gatewayFails) {
                throw new IllegalStateException("gateway down");
            }
            gatewayCalls.add(accountId + "|" + amount.toPlainString() + "|" + partial + "|" + expires);
            return new PaymentGateway.GatewayLink("plink_test1", "https://pay.example.test/l/plink_test1");
        };
        MessageSender sender = (channel, destination, body) -> {
            sent.add(channel + "|" + destination + "|" + body);
            return new MessageSender.SendResult(senderOk, "ref");
        };
        PaymentLinkStore links = new PaymentLinkStore() {
            public void save(PaymentLink l) { savedLinks.add(l); }
            public void updateStatus(String id, PaymentStatus s) {
                savedLinks.replaceAll(l -> l.linkId().equals(id) ? l.withStatus(s) : l);
            }
            public Optional<PaymentLink> findByLinkId(String id) { return savedLinks.stream().filter(l -> l.linkId().equals(id)).findFirst(); }
            public Optional<PaymentLink> findByCall(String callId) { return savedLinks.stream().filter(l -> l.callId().equals(callId)).findFirst(); }
            public List<PaymentLink> all() { return List.copyOf(savedLinks); }
            public PaymentApplied applyPayment(String id, BigDecimal amount) { throw new UnsupportedOperationException(); }
        };
        OutboundMessages outbound = new OutboundMessages() {
            public void record(String callId, Channel ch, String masked, String body, boolean ok) {
                audit.add(new Entry(callId, ch, masked, body, ok, Instant.now()));
            }
            public List<Entry> recent(int limit) { return List.copyOf(audit); }
        };
        flow = new PaymentLinkFlow(accounts, gateway, sender, links, outbound, promises, clock);
    }

    private PaymentLinkFlow.Result run(String accountId, String message, Channel requested) {
        session = new CallSession("call-" + accountId);
        session.bindAccount(accountMap.get(accountId));
        return flow.handle(session, message, requested, Language.ENGLISH, today);
    }

    // ---- tests -------------------------------------------------------------
    @Test
    void payingPartNowAndPartTomorrowSavesBothInstallments() {
        var r = run("ACC1", "still correct but I'll pay the 5000 today only send me the payment link "
                + "and the next 5000 rupees I will pay tomorrow", null);

        assertEquals(Kind.SENT, r.kind());
        var parts = r.plan().installments();
        assertEquals(2, parts.size());
        assertEquals(today, parts.get(0).date());
        assertEquals(0, new BigDecimal("5000").compareTo(parts.get(0).amount()));
        assertEquals("PAYMENT_LINK", parts.get(0).mode());
        assertEquals(today.plusDays(1), parts.get(1).date());
        assertEquals(0, new BigDecimal("5000").compareTo(parts.get(1).amount()));
        assertEquals(2, promises.find("call-ACC1").orElseThrow().installments().size());
        assertTrue(r.reply().contains("also noted"), r.reply());
    }

    @Test
    void theUsersExampleSendsALinkForTheFullBalanceAndKeepsTodaysPromise() {
        var r = run("ACC1", "I will pay now send me payment link", null);

        assertEquals(Kind.SENT, r.kind());
        var installment = r.plan().first();
        assertEquals(0, new BigDecimal("10000").compareTo(installment.amount())); // the full outstanding
        assertEquals(today, installment.date());                                  // today's promise
        assertEquals("PAYMENT_LINK", installment.mode());
        assertTrue(promises.find("call-ACC1").isPresent(), "the promise is saved");

        assertEquals(1, gatewayCalls.size());
        assertTrue(gatewayCalls.get(0).startsWith("ACC1|10000.00|true|"), gatewayCalls.get(0)); // part payment allowed
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).startsWith("WHATSAPP|9876500001|"), sent.get(0)); // the number on record, opted in
        assertTrue(sent.get(0).contains("https://pay.example.test/l/plink_test1"));
        assertTrue(r.reply().contains("ending 0001") && r.reply().contains("WhatsApp"), r.reply());
        assertFalse(r.reply().contains("9876500001"), "the full number is never repeated");
        assertEquals(PaymentStatus.SENT, savedLinks.get(0).status());
        assertEquals("****0001", r.notification().to());
        assertEquals(1, audit.size());
        assertTrue(audit.get(0).ok());
    }

    @Test
    void aCustomerWhoHasNotAgreedToWhatsappGetsAnSms() {
        var r = run("ACC3", "pay now, send me the link on whatsapp", Channel.WHATSAPP);
        assertEquals(Kind.SENT, r.kind());
        assertTrue(sent.get(0).startsWith("SMS|9876500003|"), sent.get(0));
    }

    @Test
    void theChannelTheCustomerNamedIsUsedWhenAllowed() {
        var r = run("ACC1", "pay now, sms the link", Channel.SMS);
        assertEquals(Kind.SENT, r.kind());
        assertTrue(sent.get(0).startsWith("SMS|"), sent.get(0));
    }

    @Test
    void aStatedAmountIsThePromiseButTheLinkStaysForTheWholeBalance() {
        var r = run("ACC1", "I will pay 5000 now, send the link", null);
        assertEquals(0, new BigDecimal("5000").compareTo(r.plan().first().amount()));
        assertTrue(gatewayCalls.get(0).startsWith("ACC1|10000.00|true|"), "the link is for the full balance");
    }

    @Test
    void halfTheBalanceIsWorkedOutInCode() {
        var r = run("ACC1", "aadha amount abhi pay karunga, link bhejo", null);
        assertEquals(0, new BigDecimal("5000").compareTo(r.plan().first().amount()));
    }

    @Test
    void anAmountAboveTheBalanceSendsNothing() {
        var r = run("ACC1", "I will pay 20000 now", null);
        assertEquals(Kind.AMOUNT_TOO_HIGH, r.kind());
        assertTrue(gatewayCalls.isEmpty() && sent.isEmpty() && savedLinks.isEmpty());
        assertTrue(promises.find("call-ACC1").isEmpty(), "no promise is saved either");
    }

    @Test
    void aNumberReadOutOnTheCallIsNeverUsedAndIsNotTakenForAnAmount() {
        var r = run("ACC1", "pay now, send the link to 9123456789", null);
        assertEquals(Kind.SENT, r.kind());
        assertTrue(sent.get(0).contains("9876500001"), "sent to the number on record: " + sent.get(0));
        assertFalse(sent.get(0).contains("9123456789"));
        assertEquals(0, new BigDecimal("10000").compareTo(r.plan().first().amount()));
    }

    @Test
    void withoutARegisteredMobileNothingIsSent() {
        var r = run("NOMOB", "pay now send link", null);
        assertEquals(Kind.NO_MOBILE, r.kind());
        assertTrue(gatewayCalls.isEmpty() && sent.isEmpty());
        assertTrue(promises.find("call-NOMOB").isEmpty());
    }

    @Test
    void nothingDueMeansNoLink() {
        var r = run("PAID", "pay now send link", null);
        assertEquals(Kind.NOTHING_DUE, r.kind());
        assertTrue(gatewayCalls.isEmpty() && sent.isEmpty());
    }

    @Test
    void aFailedSendKeepsThePromiseAndSaysSo() {
        senderOk = false;
        var r = run("ACC1", "pay now send link", null);
        assertEquals(Kind.SEND_FAILED, r.kind());
        assertTrue(promises.find("call-ACC1").isPresent(), "today's promise still stands");
        assertEquals(PaymentStatus.FAILED, savedLinks.get(0).status());
        assertFalse(audit.get(0).ok());
        assertTrue(r.reply().contains("could not send"), r.reply());
    }

    @Test
    void aGatewayFailureKeepsThePromiseAndSendsNothing() {
        gatewayFails = true;
        var r = run("ACC1", "pay now send link", null);
        assertEquals(Kind.SEND_FAILED, r.kind());
        assertTrue(promises.find("call-ACC1").isPresent());
        assertTrue(sent.isEmpty() && savedLinks.isEmpty());
    }
}
