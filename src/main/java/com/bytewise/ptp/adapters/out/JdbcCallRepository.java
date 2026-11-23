package com.bytewise.ptp.adapters.out;

import com.bytewise.ptp.application.CallRepository;
import com.bytewise.ptp.domain.CallSession;
import com.bytewise.ptp.domain.CallSession.Entry;
import com.bytewise.ptp.domain.Language;
import com.bytewise.ptp.application.PromiseStore;
import com.bytewise.ptp.domain.PromisePlan;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCallRepository implements CallRepository {

    private final JdbcClient jdbc;
    private final PromiseStore promises;

    public JdbcCallRepository(JdbcClient jdbc, PromiseStore promises) {
        this.jdbc = jdbc;
        this.promises = promises;
    }

    @Override
    @Transactional
    public void save(CallSession s) {
        jdbc.sql("MERGE INTO call_session (call_id, status, escalation_reason, refusals, language, account_id, "
                        + "outstanding) KEY (call_id) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(s.callId(), s.status().name(), s.escalationReason(), s.refusals(), s.language().name(),
                        s.accountId(), s.outstanding())
                .update();
        jdbc.sql("DELETE FROM call_message WHERE call_id = ?").param(s.callId()).update();
        int seq = 0;
        for (Entry e : s.entries()) {
            jdbc.sql("INSERT INTO call_message (call_id, seq, customer, message_text) VALUES (?, ?, ?, ?)")
                    .params(s.callId(), seq++, e.customer(), e.text())
                    .update();
        }
    }

    @Override
    public Optional<CallSession> find(String callId) {
        record Header(String status, String reason, int refusals, String language, String accountId,
                      BigDecimal outstanding) {
        }
        Optional<Header> header = jdbc.sql("SELECT status, escalation_reason, refusals, language, account_id, "
                        + "outstanding FROM call_session WHERE call_id = ?")
                .param(callId)
                .query((rs, n) -> new Header(rs.getString("status"), rs.getString("escalation_reason"),
                        rs.getInt("refusals"), rs.getString("language"), rs.getString("account_id"),
                        rs.getBigDecimal("outstanding")))
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<Entry> entries = jdbc.sql("SELECT customer, message_text FROM call_message WHERE call_id = ? ORDER BY seq")
                .param(callId)
                .query((rs, n) -> new Entry(rs.getBoolean("customer"), rs.getString("message_text")))
                .list();
        Header h = header.get();
        CallSession.Status status = CallSession.Status.valueOf(h.status());
        PromisePlan promise = status == CallSession.Status.PTP_SAVED ? promises.find(callId).orElse(null) : null;
        return Optional.of(CallSession.restore(callId, entries, status, promise, h.reason(), h.refusals(),
                Language.valueOf(h.language()), h.accountId(), h.outstanding()));
    }
}
