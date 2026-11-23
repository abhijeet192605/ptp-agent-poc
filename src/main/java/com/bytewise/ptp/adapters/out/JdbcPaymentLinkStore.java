package com.bytewise.ptp.adapters.out;

import com.bytewise.ptp.application.PaymentLinkStore;
import com.bytewise.ptp.domain.Channel;
import com.bytewise.ptp.domain.PaymentLink;
import com.bytewise.ptp.domain.PaymentStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPaymentLinkStore implements PaymentLinkStore {

    private static final String SELECT = "SELECT link_id, call_id, account_id, amount, accept_partial, url, channel, "
            + "destination_masked, status, paid_amount, expires_at FROM payment_link ";

    private static final RowMapper<PaymentLink> MAPPER = (rs, n) -> new PaymentLink(
            rs.getString("link_id"), rs.getString("call_id"), rs.getString("account_id"),
            rs.getBigDecimal("amount"), rs.getBoolean("accept_partial"), rs.getString("url"),
            Channel.valueOf(rs.getString("channel")), rs.getString("destination_masked"),
            PaymentStatus.valueOf(rs.getString("status")), rs.getBigDecimal("paid_amount"),
            rs.getTimestamp("expires_at").toInstant());

    private final JdbcClient jdbc;

    public JdbcPaymentLinkStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PaymentLink l) {
        jdbc.sql("INSERT INTO payment_link (link_id, call_id, account_id, amount, accept_partial, url, channel, "
                        + "destination_masked, status, paid_amount, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(l.linkId(), l.callId(), l.accountId(), l.amount(), l.acceptPartial(), l.url(),
                        l.channel().name(), l.destinationMasked(), l.status().name(), l.paidAmount(),
                        Timestamp.from(l.expiresAt()))
                .update();
    }

    @Override
    public void updateStatus(String linkId, PaymentStatus status) {
        jdbc.sql("UPDATE payment_link SET status = ? WHERE link_id = ?").params(status.name(), linkId).update();
    }

    @Override
    public Optional<PaymentLink> findByLinkId(String linkId) {
        return jdbc.sql(SELECT + "WHERE link_id = ?").param(linkId).query(MAPPER).optional();
    }

    @Override
    public Optional<PaymentLink> findByCall(String callId) {
        return jdbc.sql(SELECT + "WHERE call_id = ? ORDER BY created_at DESC, link_id DESC LIMIT 1")
                .param(callId).query(MAPPER).optional();
    }

    @Override
    public List<PaymentLink> all() {
        return jdbc.sql(SELECT + "ORDER BY created_at DESC, link_id DESC").query(MAPPER).list();
    }

    @Override
    @Transactional
    public PaymentApplied applyPayment(String linkId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("the payment must be more than zero");
        }
        PaymentLink link = findByLinkId(linkId)
                .orElseThrow(() -> new IllegalArgumentException("unknown payment link " + linkId));
        if (link.status() == PaymentStatus.EXPIRED || link.status() == PaymentStatus.FAILED) {
            throw new IllegalArgumentException("the link is " + link.status().name().toLowerCase() + " and cannot be paid");
        }
        if (amount.compareTo(link.remaining()) > 0) {
            throw new IllegalArgumentException("the payment is more than the " + link.remaining().toPlainString()
                    + " still due on the link");
        }
        if (!link.acceptPartial() && amount.compareTo(link.amount()) != 0) {
            throw new IllegalArgumentException("this link does not accept a part payment");
        }

        BigDecimal paid = link.paidAmount().add(amount);
        PaymentStatus status = paid.compareTo(link.amount()) >= 0 ? PaymentStatus.PAID : PaymentStatus.PARTIALLY_PAID;
        jdbc.sql("UPDATE payment_link SET paid_amount = ?, status = ? WHERE link_id = ?")
                .params(paid, status.name(), linkId).update();
        jdbc.sql("UPDATE loan_account SET outstanding = GREATEST(outstanding - ?, 0) WHERE account_id = ?")
                .params(amount, link.accountId()).update();

        BigDecimal outstanding = jdbc.sql("SELECT outstanding FROM loan_account WHERE account_id = ?")
                .param(link.accountId()).query(BigDecimal.class).single();
        PaymentLink updated = new PaymentLink(link.linkId(), link.callId(), link.accountId(), link.amount(),
                link.acceptPartial(), link.url(), link.channel(), link.destinationMasked(), status, paid,
                link.expiresAt());
        return new PaymentApplied(updated, outstanding);
    }
}
