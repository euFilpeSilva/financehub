ALTER TABLE bills ADD (
  bank_account_id VARCHAR2(36 CHAR)
);

ALTER TABLE incomes ADD (
  bank_account_id VARCHAR2(36 CHAR)
);

ALTER TABLE bills ADD CONSTRAINT fk_bills_bank_account
  FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id) ON DELETE SET NULL;

ALTER TABLE incomes ADD CONSTRAINT fk_incomes_bank_account
  FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id) ON DELETE SET NULL;

CREATE INDEX idx_bills_bank_account_id ON bills (bank_account_id);
CREATE INDEX idx_incomes_bank_account_id ON incomes (bank_account_id);
