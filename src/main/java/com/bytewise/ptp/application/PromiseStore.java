package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.PromisePlan;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Record of saved promises, one plan (one or more installments) per call. */
public interface PromiseStore {

    /** A saved plan together with the call it was made on. */
    record SavedPromise(String callId, PromisePlan plan) {
    }

    /** @return false if this call already has a saved promise */
    boolean save(String callId, PromisePlan plan);

    Optional<PromisePlan> find(String callId);

    Map<String, PromisePlan> all();

    /** Every promise made on calls for this loan account, newest first. */
    List<SavedPromise> findByAccount(String accountId);
}
