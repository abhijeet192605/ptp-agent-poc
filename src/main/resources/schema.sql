CREATE TABLE IF NOT EXISTS loan_account (
    account_id    VARCHAR(50)   PRIMARY KEY,
    customer_name VARCHAR(100)  NOT NULL,
    outstanding   DECIMAL(18,2) NOT NULL
);

-- Added with payment links: the number on record, and whether the customer agreed to WhatsApp messages
ALTER TABLE loan_account ADD COLUMN IF NOT EXISTS mobile VARCHAR(20);
ALTER TABLE loan_account ADD COLUMN IF NOT EXISTS whatsapp_opt_in BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS payment_link (
    link_id            VARCHAR(60)   PRIMARY KEY,
    call_id            VARCHAR(100)  NOT NULL,
    account_id         VARCHAR(50)   NOT NULL,
    amount             DECIMAL(18,2) NOT NULL,
    accept_partial     BOOLEAN       NOT NULL,
    url                VARCHAR(300)  NOT NULL,
    channel            VARCHAR(20)   NOT NULL,
    destination_masked VARCHAR(30)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    paid_amount        DECIMAL(18,2) NOT NULL,
    expires_at         TIMESTAMP     NOT NULL,
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- Every message the agent sends a customer: an audit trail of exactly what went out and where
CREATE TABLE IF NOT EXISTS outbound_message (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_id            VARCHAR(100),
    channel            VARCHAR(20)   NOT NULL,
    destination_masked VARCHAR(30)   NOT NULL,
    body               VARCHAR(1000) NOT NULL,
    ok                 BOOLEAN       NOT NULL,
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS promise (
    call_id         VARCHAR(100)  PRIMARY KEY,
    amount          DECIMAL(18,2) NOT NULL,
    promise_date    DATE          NOT NULL,
    pay_mode        VARCHAR(20),
    conditions_text VARCHAR(200),
    saved_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- Promises: one row per call, with one or more installments. (The older single-payment "promise" table
-- above is kept only so databases created earlier are carried over below.)
CREATE TABLE IF NOT EXISTS ptp (
    call_id         VARCHAR(100) PRIMARY KEY,
    conditions_text VARCHAR(200),
    saved_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ptp_installment (
    call_id      VARCHAR(100)  NOT NULL,
    seq          INT           NOT NULL,
    amount       DECIMAL(18,2) NOT NULL,
    promise_date DATE          NOT NULL,
    pay_mode     VARCHAR(20),
    PRIMARY KEY (call_id, seq)
);

INSERT INTO ptp (call_id, conditions_text, saved_at)
    SELECT call_id, conditions_text, saved_at FROM promise WHERE call_id NOT IN (SELECT call_id FROM ptp);
INSERT INTO ptp_installment (call_id, seq, amount, promise_date, pay_mode)
    SELECT call_id, 0, amount, promise_date, pay_mode FROM promise
    WHERE call_id NOT IN (SELECT call_id FROM ptp_installment);

CREATE TABLE IF NOT EXISTS call_session (
    call_id           VARCHAR(100) PRIMARY KEY,
    status            VARCHAR(20)  NOT NULL,
    escalation_reason VARCHAR(200),
    refusals          INT          NOT NULL,
    language          VARCHAR(20)  NOT NULL
);

-- Added after the first release: keeps databases created earlier working
ALTER TABLE call_session ADD COLUMN IF NOT EXISTS account_id VARCHAR(50);
ALTER TABLE call_session ADD COLUMN IF NOT EXISTS outstanding DECIMAL(18,2);

CREATE TABLE IF NOT EXISTS call_message (
    call_id      VARCHAR(100)  NOT NULL,
    seq          INT           NOT NULL,
    customer     BOOLEAN       NOT NULL,
    message_text VARCHAR(2000) NOT NULL,
    PRIMARY KEY (call_id, seq)
);
