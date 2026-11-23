package com.bytewise.ptp.adapters.out;

import com.bytewise.ptp.domain.CallSession;
import com.bytewise.ptp.domain.Channel;
import com.bytewise.ptp.domain.PaymentLink;
import com.bytewise.ptp.domain.PaymentStatus;
import com.bytewise.ptp.domain.Installment;
import com.bytewise.ptp.domain.Language;
import com.bytewise.ptp.domain.LoanAccount;
import com.bytewise.ptp.domain.PromisePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real schema.sql against an in-memory H2, no Spring context. */
class JdbcRepositoriesTest {

    private DriverManagerDataSource ds;
    private JdbcPromiseStore promises;
    private JdbcCallRepository calls;

    @BeforeEach
    void setUp() {
        ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        var jdbc = JdbcClient.create(ds);
        promises = new JdbcPromiseStore(jdbc);
        calls = new JdbcCallRepository(jdbc, promises);
    }

    private static PromisePlan single() {
        return new PromisePlan(List.of(
                new Installment(new BigDecimal("2500"), LocalDate.of(2026, 11, 5), "CASH")), "salary");
    }

    private static PromisePlan split() {
        return new PromisePlan(List.of(
                new Installment(new BigDecimal("5000"), LocalDate.of(2026, 10, 20), "CHEQUE"),
                new Installment(new BigDecimal("5000"), LocalDate.of(2026, 10, 22), "CASH")), null);
    }

    @Test
    void promiseRoundTripAndOnePerCall() {
        assertTrue(promises.save("c1", single()));
        assertFalse(promises.save("c1", single()));

        var loaded = promises.all().get("c1");
        assertEquals(1, loaded.installments().size());
        assertEquals(0, new BigDecimal("2500").compareTo(loaded.first().amount()));
        assertEquals(LocalDate.of(2026, 11, 5), loaded.first().date());
        assertEquals("CASH", loaded.first().mode());
        assertEquals("salary", loaded.conditions());
    }

    @Test
    void aSplitPaymentKeepsEveryInstallmentInOrder() {
        assertTrue(promises.save("c1", split()));

        var loaded = promises.find("c1").orElseThrow();
        assertEquals(2, loaded.installments().size());
        assertEquals(LocalDate.of(2026, 10, 20), loaded.installments().get(0).date());
        assertEquals("CHEQUE", loaded.installments().get(0).mode());
        assertEquals(LocalDate.of(2026, 10, 22), loaded.installments().get(1).date());
        assertEquals("CASH", loaded.installments().get(1).mode());
        assertEquals(0, new BigDecimal("10000").compareTo(loaded.total()));
    }

    @Test
    void severalCallsAreKeptApart() {
        promises.save("a", single());
        promises.save("b", split());
        assertEquals(2, promises.all().size());
        assertEquals(1, promises.all().get("a").installments().size());
        assertEquals(2, promises.all().get("b").installments().size());
        assertTrue(promises.find("nope").isEmpty());
    }

    @Test
    void promisesCanBeFoundByAccountNewestFirst() {
        var jdbc = JdbcClient.create(ds);
        var acc1 = new LoanAccount("ACC1", "Ramesh Patil", new BigDecimal("10000"));
        var acc2 = new LoanAccount("ACC2", "Sunita Sharma", new BigDecimal("25000"));
        for (var entry : List.of(java.util.Map.entry("c10", acc1), java.util.Map.entry("c11", acc1),
                java.util.Map.entry("c12", acc2))) {
            var s = new CallSession(entry.getKey());
            s.bindAccount(entry.getValue());
            s.addCustomerMessage("hello");
            calls.save(s);
            promises.save(entry.getKey(), single());
        }
        jdbc.sql("UPDATE ptp SET saved_at = TIMESTAMP '2026-10-01 10:00:00' WHERE call_id = 'c10'").update();
        jdbc.sql("UPDATE ptp SET saved_at = TIMESTAMP '2026-10-02 10:00:00' WHERE call_id = 'c11'").update();

        var forAcc1 = promises.findByAccount("ACC1");
        assertEquals(List.of("c11", "c10"), forAcc1.stream().map(p -> p.callId()).toList());
        assertEquals(1, forAcc1.get(0).plan().installments().size());
        assertEquals(List.of("c12"), promises.findByAccount("ACC2").stream().map(p -> p.callId()).toList());
        assertTrue(promises.findByAccount("NOPE").isEmpty());
    }

    @Test
    void promisesSavedBeforeInstallmentsExistedAreCarriedOver() {
        var jdbc = JdbcClient.create(ds);
        jdbc.sql("INSERT INTO promise (call_id, amount, promise_date, pay_mode, conditions_text) "
                + "VALUES ('old1', 1800, DATE '2026-10-20', 'CASH', NULL)").update();
        // the script runs again at every start-up
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);

        var loaded = promises.find("old1").orElseThrow();
        assertEquals(1, loaded.installments().size());
        assertEquals(0, new BigDecimal("1800").compareTo(loaded.first().amount()));
        assertEquals("CASH", loaded.first().mode());
    }

    @Test
    void sessionSurvivesSaveAndReload() {
        var s = new CallSession("c2");
        s.addCustomerMessage("Salary aane ke baad dunga");
        s.addAgentMessage("Kya aap koi tentative tareekh aur amount bata sakte hain?");
        s.addCustomerMessage("Nahi pata");
        s.observeLanguage(Language.HINGLISH);
        s.recordRefusal();
        calls.save(s);

        var back = calls.find("c2").orElseThrow();
        assertEquals(CallSession.Status.IN_PROGRESS, back.status());
        assertEquals(1, back.refusals());
        assertEquals(Language.HINGLISH, back.language());
        assertEquals(3, back.entries().size());
        assertEquals("Salary aane ke baad dunga Nahi pata", back.allCustomerText());
    }

    @Test
    void savedAndEscalatedStatesAreRestored() {
        promises.save("c3", split());
        var saved = new CallSession("c3");
        saved.addCustomerMessage("20 tarikh la 50% cheque ani 22 tarikh la 50% cash");
        saved.markSaved(split());
        calls.save(saved);

        var esc = new CallSession("c4");
        esc.addCustomerMessage("manager se baat karao");
        esc.escalate("asked for a person");
        calls.save(esc);

        var back3 = calls.find("c3").orElseThrow();
        assertEquals(CallSession.Status.PTP_SAVED, back3.status());
        assertEquals(2, back3.savedPromise().installments().size());
        assertEquals(LocalDate.of(2026, 10, 22), back3.savedPromise().installments().get(1).date());

        var back4 = calls.find("c4").orElseThrow();
        assertEquals(CallSession.Status.ESCALATED, back4.status());
        assertEquals("asked for a person", back4.escalationReason());
    }

    @Test
    void savingTwiceReplacesMessagesInsteadOfDuplicating() {
        var s = new CallSession("c5");
        s.addCustomerMessage("hello");
        calls.save(s);
        s.addAgentMessage("hi");
        calls.save(s);
        assertEquals(2, calls.find("c5").orElseThrow().entries().size());
    }

    @Test
    void loanAccountLookup() {
        var jdbc = JdbcClient.create(ds);
        jdbc.sql("INSERT INTO loan_account (account_id, customer_name, outstanding) VALUES ('ACC1', 'Ramesh Patil', 10000.00)").update();
        var accounts = new JdbcLoanAccounts(jdbc);
        var a = accounts.find("ACC1").orElseThrow();
        assertEquals("Ramesh Patil", a.customerName());
        assertEquals(0, new BigDecimal("10000").compareTo(a.outstanding()));
        assertTrue(accounts.find("nope").isEmpty());
    }

    @Test
    void loanAccountListIsOrderedById() {
        var jdbc = JdbcClient.create(ds);
        jdbc.sql("INSERT INTO loan_account (account_id, customer_name, outstanding) VALUES ('ACC2', 'Sunita Sharma', 25000.00)").update();
        jdbc.sql("INSERT INTO loan_account (account_id, customer_name, outstanding) VALUES ('ACC1', 'Ramesh Patil', 10000.00)").update();
        var all = new JdbcLoanAccounts(jdbc).all();
        assertEquals(2, all.size());
        assertEquals("ACC1", all.get(0).accountId());
        assertEquals("ACC2", all.get(1).accountId());
    }

    private PaymentLink link(String id, String call, String amount) {
        return new PaymentLink(id, call, "ACC1", new BigDecimal(amount), true, "https://pay.example.test/l/" + id,
                Channel.WHATSAPP, "****0001", PaymentStatus.SENT, BigDecimal.ZERO,
                java.time.Instant.parse("2026-10-15T10:00:00Z"));
    }

    private JdbcPaymentLinkStore paymentStoreWithAccount() {
        var jdbc = JdbcClient.create(ds);
        jdbc.sql("INSERT INTO loan_account (account_id, customer_name, outstanding, mobile, whatsapp_opt_in) "
                + "VALUES ('ACC1', 'Ramesh Patil', 10000.00, '9876500001', TRUE)").update();
        return new JdbcPaymentLinkStore(jdbc);
    }

    @Test
    void accountsCarryTheMobileNumberAndWhatsappConsent() {
        paymentStoreWithAccount();
        var a = new JdbcLoanAccounts(JdbcClient.create(ds)).find("ACC1").orElseThrow();
        assertEquals("9876500001", a.mobile());
        assertTrue(a.whatsappOptIn());
        assertTrue(a.hasMobile());
    }

    @Test
    void aPartPaymentOnALinkIsFineAndLowersTheBalance() {
        var store = paymentStoreWithAccount();
        store.save(link("L1", "c1", "10000.00"));

        // the user's example: 10000 outstanding, the link is shared, the customer pays 5000
        var applied = store.applyPayment("L1", new BigDecimal("5000"));
        assertEquals(PaymentStatus.PARTIALLY_PAID, applied.link().status());
        assertEquals(0, new BigDecimal("5000").compareTo(applied.link().paidAmount()));
        assertEquals(0, new BigDecimal("5000").compareTo(applied.accountOutstanding()));
        assertEquals(0, new BigDecimal("5000").compareTo(applied.link().remaining()));

        var rest = store.applyPayment("L1", new BigDecimal("5000"));
        assertEquals(PaymentStatus.PAID, rest.link().status());
        assertEquals(0, BigDecimal.ZERO.compareTo(rest.accountOutstanding()));
        assertEquals(PaymentStatus.PAID, store.findByLinkId("L1").orElseThrow().status());
    }

    @Test
    void aPaymentLargerThanWhatIsLeftOnTheLinkIsRefused() {
        var store = paymentStoreWithAccount();
        store.save(link("L1", "c1", "10000.00"));
        store.applyPayment("L1", new BigDecimal("6000"));
        var e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> store.applyPayment("L1", new BigDecimal("5000")));
        assertTrue(e.getMessage().contains("still due"), e.getMessage());
        // nothing was taken from the balance by the refused payment
        assertEquals(0, new BigDecimal("4000").compareTo(
                new JdbcLoanAccounts(JdbcClient.create(ds)).find("ACC1").orElseThrow().outstanding()));
    }

    @Test
    void badPaymentsAreRefused() {
        var store = paymentStoreWithAccount();
        store.save(link("L1", "c1", "10000.00"));
        for (String amount : new String[]{"0", "-5"}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> store.applyPayment("L1", new BigDecimal(amount)));
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> store.applyPayment("NOPE", new BigDecimal("100")));
        store.updateStatus("L1", PaymentStatus.EXPIRED);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> store.applyPayment("L1", new BigDecimal("100")));
    }

    @Test
    void linksAreFoundByCallAndListedNewestFirst() {
        var store = paymentStoreWithAccount();
        store.save(link("L1", "c1", "10000.00"));
        store.save(link("L2", "c2", "10000.00"));
        assertEquals("L1", store.findByCall("c1").orElseThrow().linkId());
        assertTrue(store.findByCall("nope").isEmpty());
        assertEquals(2, store.all().size());
    }

    @Test
    void sentMessagesAreKeptAsAnAuditTrail() {
        var outbound = new JdbcOutboundMessages(JdbcClient.create(ds));
        outbound.record("c1", Channel.WHATSAPP, "****0001", "pay here https://pay.example.test/l/L1", true);
        outbound.record("c2", Channel.SMS, "****0002", "second", false);
        var recent = outbound.recent(10);
        assertEquals(2, recent.size());
        assertEquals("c2", recent.get(0).callId());                    // newest first
        assertFalse(recent.get(0).ok());
        assertEquals(Channel.WHATSAPP, recent.get(1).channel());
    }

    @Test
    void sessionKeepsItsAccountAndOutstandingSnapshot() {
        var s = new CallSession("c6");
        s.bindAccount(new LoanAccount("ACC1", "Ramesh Patil", new BigDecimal("10000.00")));
        s.addCustomerMessage("50% dunga");
        calls.save(s);

        var back = calls.find("c6").orElseThrow();
        assertEquals("ACC1", back.accountId());
        assertEquals(0, new BigDecimal("10000").compareTo(back.outstanding()));
    }

    @Test
    void unknownCallIsEmpty() {
        assertTrue(calls.find("nope").isEmpty());
    }
}
