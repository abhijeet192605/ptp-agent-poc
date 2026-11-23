package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.Channel;

import java.time.Instant;
import java.util.List;

/** An audit trail of every message the agent sent a customer: what went out, on which channel, to which number. */
public interface OutboundMessages {

    record Entry(String callId, Channel channel, String destinationMasked, String body, boolean ok, Instant createdAt) {
    }

    void record(String callId, Channel channel, String destinationMasked, String body, boolean ok);

    /** Newest first. */
    List<Entry> recent(int limit);
}
