package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.PromisePlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Test double for PromiseStore. */
class InMemoryPromiseStore implements PromiseStore {

    private final Map<String, PromisePlan> byCall = new LinkedHashMap<>();

    @Override
    public boolean save(String callId, PromisePlan plan) {
        return byCall.putIfAbsent(callId, plan) == null;
    }

    @Override
    public Optional<PromisePlan> find(String callId) {
        return Optional.ofNullable(byCall.get(callId));
    }

    @Override
    public Map<String, PromisePlan> all() {
        return Map.copyOf(byCall);
    }

    @Override
    public java.util.List<SavedPromise> findByAccount(String accountId) {
        return java.util.List.of(); // the tools under test never look up earlier calls
    }
}
