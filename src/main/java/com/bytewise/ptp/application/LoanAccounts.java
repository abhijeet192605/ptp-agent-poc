package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.LoanAccount;

import java.util.List;
import java.util.Optional;

/** Lookup of the loan behind a call. */
public interface LoanAccounts {

    Optional<LoanAccount> find(String accountId);

    /** Every account, for pickers in test tools. */
    List<LoanAccount> all();
}
