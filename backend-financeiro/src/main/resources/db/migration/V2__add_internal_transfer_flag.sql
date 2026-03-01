ALTER TABLE bills ADD (
  internal_transfer NUMBER(1) DEFAULT 0 NOT NULL
);

ALTER TABLE bills ADD CONSTRAINT ck_bills_internal_transfer_bool CHECK (internal_transfer IN (0, 1));

ALTER TABLE incomes ADD (
  internal_transfer NUMBER(1) DEFAULT 0 NOT NULL
);

ALTER TABLE incomes ADD CONSTRAINT ck_incomes_internal_transfer_bool CHECK (internal_transfer IN (0, 1));

