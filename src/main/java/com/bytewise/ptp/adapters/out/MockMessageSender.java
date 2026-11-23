package com.bytewise.ptp.adapters.out;

import com.bytewise.ptp.application.MessageSender;
import com.bytewise.ptp.domain.Channel;
import com.bytewise.ptp.domain.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stand-in for an SMS or WhatsApp provider: it sends nothing. The audit table written by the caller is how you see
 * what would have gone out. A real sender needs a registered sender ID and approved templates.
 */
@Component
public class MockMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MockMessageSender.class);

    @Override
    public SendResult send(Channel channel, String destination, String body) {
        log.debug("[mock sender] {} to {}: {}", channel, PhoneNumbers.mask(destination), body);
        return new SendResult(true, "mock-" + UUID.randomUUID());
    }
}
