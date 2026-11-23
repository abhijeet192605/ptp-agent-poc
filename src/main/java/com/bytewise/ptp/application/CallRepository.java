package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.CallSession;

import java.util.Optional;

/** Storage for call sessions, so an in-progress call survives a restart. */
public interface CallRepository {

    void save(CallSession session);

    Optional<CallSession> find(String callId);
}
