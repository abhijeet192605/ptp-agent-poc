-- Sample loan accounts for testing. Rows are only inserted when missing, so a balance reduced by a payment
-- is not reset when the app restarts. To start clean, stop the app and delete the ./data folder.
INSERT INTO loan_account (account_id, customer_name, outstanding)
    SELECT 'ACC1001', 'Ramesh Patil', 10000.00 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM loan_account WHERE account_id = 'ACC1001');
INSERT INTO loan_account (account_id, customer_name, outstanding)
    SELECT 'ACC1002', 'Sunita Sharma', 25000.00 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM loan_account WHERE account_id = 'ACC1002');
INSERT INTO loan_account (account_id, customer_name, outstanding)
    SELECT 'ACC1003', 'Amit Verma', 48500.50 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM loan_account WHERE account_id = 'ACC1003');
INSERT INTO loan_account (account_id, customer_name, outstanding)
    SELECT 'ACC1004', 'Meera Joshi', 8000.00 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM loan_account WHERE account_id = 'ACC1004');

-- Fictional numbers. ACC1003 has not agreed to WhatsApp, which shows the fall-back to SMS.
UPDATE loan_account SET mobile = '9876500001', whatsapp_opt_in = TRUE  WHERE account_id = 'ACC1001' AND mobile IS NULL;
UPDATE loan_account SET mobile = '9876500002', whatsapp_opt_in = TRUE  WHERE account_id = 'ACC1002' AND mobile IS NULL;
UPDATE loan_account SET mobile = '9876500003', whatsapp_opt_in = FALSE WHERE account_id = 'ACC1003' AND mobile IS NULL;
UPDATE loan_account SET mobile = '9876500004', whatsapp_opt_in = TRUE  WHERE account_id = 'ACC1004' AND mobile IS NULL;
