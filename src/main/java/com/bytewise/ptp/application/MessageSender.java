package com.bytewise.ptp.application;

import com.bytewise.ptp.domain.Channel;

/**
 * Sends a text to a customer by SMS or WhatsApp. The POC uses a mock. A real one needs a registered sender and
 * approved templates (DLT for SMS in India, a WhatsApp Business template) and the customer's consent.
 */
public interface MessageSender {

    record SendResult(boolean ok, String reference) {
    }

    SendResult send(Channel channel, String destination, String body);
}
