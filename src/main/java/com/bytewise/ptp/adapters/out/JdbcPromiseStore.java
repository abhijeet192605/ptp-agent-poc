package com.bytewise.ptp.adapters.out;

import com.bytewise.ptp.application.PromiseStore;
import com.bytewise.ptp.domain.Installment;
import com.bytewise.ptp.domain.PromisePlan;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcPromiseStore implements PromiseStore {

    private static final String SELECT = "SELECT p.call_id, p.conditions_text, i.amount, i.promise_date, i.pay_mode "
            + "FROM ptp p JOIN ptp_installment i ON i.call_id = p.call_id ";

    private final JdbcClient jdbc;

    public JdbcPromiseStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean save(String callId, PromisePlan plan) {
        try {
            jdbc.sql("INSERT INTO ptp (call_id, conditions_text) VALUES (?, ?)")
                    .params(callId, plan.conditions())
                    .update();
        } catch (DuplicateKeyException e) {
            return false;
        }
        int seq = 0;
        for (Installment i : plan.installments()) {
            jdbc.sql("INSERT INTO ptp_installment (call_id, seq, amount, promise_date, pay_mode) VALUES (?, ?, ?, ?, ?)")
                    .params(callId, seq++, i.amount(), Date.valueOf(i.date()), i.mode())
                    .update();
        }
        return true;
    }

    @Override
    public Optional<PromisePlan> find(String callId) {
        return Optional.ofNullable(load(SELECT + "WHERE p.call_id = ? ORDER BY p.saved_at, p.call_id, i.seq", callId)
                .get(callId));
    }

    @Override
    public Map<String, PromisePlan> all() {
        return load(SELECT + "ORDER BY p.saved_at, p.call_id, i.seq");
    }

    @Override
    public List<SavedPromise> findByAccount(String accountId) {
        Map<String, PromisePlan> byCall = load(SELECT
                + "JOIN call_session s ON s.call_id = p.call_id WHERE s.account_id = ? "
                + "ORDER BY p.saved_at DESC, p.call_id, i.seq", accountId);
        List<SavedPromise> out = new ArrayList<>();
        byCall.forEach((id, plan) -> out.add(new SavedPromise(id, plan)));
        return out;
    }

    /** Runs a query that returns one row per installment and groups the rows into plans, keeping the row order. */
    private Map<String, PromisePlan> load(String sql, Object... params) {
        Map<String, String> conditions = new LinkedHashMap<>();
        Map<String, List<Installment>> installments = new LinkedHashMap<>();
        var spec = jdbc.sql(sql);
        if (params.length > 0) {
            spec = spec.params(params);
        }
        spec.query((rs, n) -> {
            String id = rs.getString("call_id");
            conditions.put(id, rs.getString("conditions_text"));
            installments.computeIfAbsent(id, k -> new ArrayList<>()).add(new Installment(
                    rs.getBigDecimal("amount"), rs.getDate("promise_date").toLocalDate(), rs.getString("pay_mode")));
            return null;
        }).list();

        Map<String, PromisePlan> out = new LinkedHashMap<>();
        installments.forEach((id, list) -> out.put(id, new PromisePlan(list, conditions.get(id))));
        return out;
    }
}
