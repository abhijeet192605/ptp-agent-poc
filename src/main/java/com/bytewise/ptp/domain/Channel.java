package com.bytewise.ptp.domain;

/** How a payment link reaches the customer. */
public enum Channel {
    SMS("SMS"),
    WHATSAPP("WhatsApp");

    private final String display;

    Channel(String display) {
        this.display = display;
    }

    /** The name as a customer would say it. */
    public String display() {
        return display;
    }
}
