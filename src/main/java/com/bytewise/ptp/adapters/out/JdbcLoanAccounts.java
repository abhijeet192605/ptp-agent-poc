package com.bytewise.ptp.adapters.out;

import com.bytewise.ptp.application.LoanAccounts;
import com.bytewise.ptp.domain.LoanAccount;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcLoanAccounts implements LoanAccounts {

    private static final String SELECT =
            "SELECT account_id, customer_name, outstanding, mobile, whatsapp_opt_in FROM loan_account ";

    private static final RowMapper<LoanAccount> MAPPER = (rs, n) -> new LoanAccount(
            rs.getString("account_id"), rs.getString("customer_name"), rs.getBigDecimal("outstanding"),
            rs.getString("mobile"), rs.getBoolean("whatsapp_opt_in"));

    private final JdbcClient jdbc;

    public JdbcLoanAccounts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LoanAccount> all() {
        return jdbc.sql(SELECT + "ORDER BY account_id").query(MAPPER).list();
    }

    @Override
    public Optional<LoanAccount> find(String accountId) {
        return jdbc.sql(SELECT + "WHERE account_id = ?").param(accountId).query(MAPPER).optional();
    }
}
