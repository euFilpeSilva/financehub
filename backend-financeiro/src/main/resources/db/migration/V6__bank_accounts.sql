CREATE TABLE bank_accounts (
  id VARCHAR2(36 CHAR) NOT NULL,
  label VARCHAR2(120 CHAR) NOT NULL,
  bank_id VARCHAR2(20 CHAR) NOT NULL,
  branch_id VARCHAR2(20 CHAR),
  account_id VARCHAR2(40 CHAR) NOT NULL,
  primary_income NUMBER(1) DEFAULT 0 NOT NULL,
  active NUMBER(1) DEFAULT 1 NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_bank_accounts PRIMARY KEY (id),
  CONSTRAINT ck_bank_accounts_primary_income_bool CHECK (primary_income IN (0, 1)),
  CONSTRAINT ck_bank_accounts_active_bool CHECK (active IN (0, 1))
);

CREATE UNIQUE INDEX uq_bank_accounts_bank_and_account ON bank_accounts (bank_id, account_id);
CREATE INDEX idx_bank_accounts_primary_income ON bank_accounts (primary_income);
