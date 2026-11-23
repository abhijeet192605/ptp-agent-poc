package com.bytewise.ptp.adapters.out;

import com.bytewise.ptp.application.OutboundMessages;
import com.bytewise.ptp.domain.Channel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcOutboundMessages implements OutboundMessages {

    private final JdbcClient jdbc;

    public JdbcOutboundMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String callId, Channel channel, String destinationMasked, String body, boolean ok) {
        jdbc.sql("INSERT INTO outbound_message (call_id, channel, destination_masked, body, ok) VALUES (?, ?, ?, ?, ?)")
                .params(callId, channel.name(), destinationMasked, body, ok)
                .update();
    }

    @Override
    public List<Entry> recent(int limit) {
        return jdbc.sql("SELECT call_id, channel, destination_masked, body, ok, created_at FROM outbound_message "
                        + "ORDER BY id DESC LIMIT ?")
                .param(limit)
                .query((rs, n) -> new Entry(rs.getString("call_id"), Channel.valueOf(rs.getString("channel")),
                        rs.getString("destination_masked"), rs.getString("body"), rs.getBoolean("ok"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
