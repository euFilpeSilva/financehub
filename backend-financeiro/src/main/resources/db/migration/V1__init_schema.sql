CREATE TABLE bills (
  id VARCHAR2(36 CHAR) NOT NULL,
  description VARCHAR2(255 CHAR) NOT NULL,
  category VARCHAR2(120 CHAR) NOT NULL,
  amount NUMBER(15,2) NOT NULL,
  due_date DATE NOT NULL,
  recurring NUMBER(1) DEFAULT 0 NOT NULL,
  paid NUMBER(1) DEFAULT 0 NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_bills PRIMARY KEY (id),
  CONSTRAINT ck_bills_amount_positive CHECK (amount > 0),
  CONSTRAINT ck_bills_recurring_bool CHECK (recurring IN (0, 1)),
  CONSTRAINT ck_bills_paid_bool CHECK (paid IN (0, 1))
);

CREATE INDEX idx_bills_due_date ON bills (due_date);
CREATE INDEX idx_bills_category ON bills (category);

CREATE TABLE incomes (
  id VARCHAR2(36 CHAR) NOT NULL,
  source VARCHAR2(255 CHAR) NOT NULL,
  category VARCHAR2(120 CHAR) NOT NULL,
  amount NUMBER(15,2) NOT NULL,
  received_at DATE NOT NULL,
  recurring NUMBER(1) DEFAULT 0 NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_incomes PRIMARY KEY (id),
  CONSTRAINT ck_incomes_amount_positive CHECK (amount > 0),
  CONSTRAINT ck_incomes_recurring_bool CHECK (recurring IN (0, 1))
);

CREATE INDEX idx_incomes_received_at ON incomes (received_at);
CREATE INDEX idx_incomes_category ON incomes (category);

CREATE TABLE planning_goals (
  id VARCHAR2(36 CHAR) NOT NULL,
  title VARCHAR2(180 CHAR) NOT NULL,
  target_amount NUMBER(15,2) NOT NULL,
  current_amount NUMBER(15,2) NOT NULL,
  target_date DATE NOT NULL,
  notes CLOB,
  complete NUMBER(1) DEFAULT 0 NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_planning_goals PRIMARY KEY (id),
  CONSTRAINT ck_planning_target_amount_positive CHECK (target_amount > 0),
  CONSTRAINT ck_planning_current_amount_nonneg CHECK (current_amount >= 0),
  CONSTRAINT ck_planning_complete_bool CHECK (complete IN (0, 1))
);

CREATE INDEX idx_planning_target_date ON planning_goals (target_date);

CREATE TABLE audit_events (
  id VARCHAR2(36 CHAR) NOT NULL,
  entity_type VARCHAR2(60 CHAR) NOT NULL,
  entity_id VARCHAR2(120 CHAR) NOT NULL,
  action VARCHAR2(40 CHAR) NOT NULL,
  message VARCHAR2(500 CHAR) NOT NULL,
  amount NUMBER(15,2),
  event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT pk_audit_events PRIMARY KEY (id)
);

CREATE INDEX idx_audit_event_timestamp ON audit_events (event_timestamp);
CREATE INDEX idx_audit_entity ON audit_events (entity_type, entity_id);

CREATE TABLE retention_settings (
  id NUMBER(10) NOT NULL,
  trash_retention_days NUMBER(10) NOT NULL,
  audit_retention_days NUMBER(10) NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_retention_settings PRIMARY KEY (id),
  CONSTRAINT ck_retention_singleton CHECK (id = 1),
  CONSTRAINT ck_trash_retention_range CHECK (trash_retention_days BETWEEN 1 AND 3650),
  CONSTRAINT ck_audit_retention_range CHECK (audit_retention_days BETWEEN 1 AND 3650)
);

CREATE TABLE app_preferences (
  id NUMBER(10) NOT NULL,
  default_bill_category VARCHAR2(120 CHAR) NOT NULL,
  default_bill_recurring NUMBER(1) DEFAULT 0 NOT NULL,
  default_bill_due_day NUMBER(10) NOT NULL,
  default_income_category VARCHAR2(120 CHAR) NOT NULL,
  default_income_recurring NUMBER(1) DEFAULT 0 NOT NULL,
  default_income_received_day NUMBER(10) NOT NULL,
  default_dashboard_mode VARCHAR2(20 CHAR) NOT NULL,
  default_dashboard_month_comparison_offset NUMBER(10) NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_app_preferences PRIMARY KEY (id),
  CONSTRAINT ck_preferences_singleton CHECK (id = 1),
  CONSTRAINT ck_preferences_bill_recurring_bool CHECK (default_bill_recurring IN (0, 1)),
  CONSTRAINT ck_preferences_income_recurring_bool CHECK (default_income_recurring IN (0, 1)),
  CONSTRAINT ck_preferences_due_day CHECK (default_bill_due_day BETWEEN 1 AND 31),
  CONSTRAINT ck_preferences_received_day CHECK (default_income_received_day BETWEEN 1 AND 31),
  CONSTRAINT ck_preferences_mode CHECK (default_dashboard_mode IN ('month', 'range')),
  CONSTRAINT ck_preferences_month_offset CHECK (default_dashboard_month_comparison_offset BETWEEN 1 AND 12)
);

INSERT INTO bills (id, description, category, amount, due_date, recurring, paid)
VALUES ('b1', 'Aluguel', 'Moradia', 2400.00, DATE '2026-02-05', 1, 1);

INSERT INTO bills (id, description, category, amount, due_date, recurring, paid)
VALUES ('b2', 'Energia', 'Utilidades', 220.00, DATE '2026-02-12', 1, 0);

INSERT INTO bills (id, description, category, amount, due_date, recurring, paid)
VALUES ('b3', 'Supermercado', 'Alimentacao', 950.00, DATE '2026-02-16', 0, 0);

INSERT INTO incomes (id, source, category, amount, received_at, recurring)
VALUES ('i1', 'Salario', 'Trabalho', 7600.00, DATE '2026-02-01', 1);

INSERT INTO incomes (id, source, category, amount, received_at, recurring)
VALUES ('i2', 'Freelance', 'Extra', 1800.00, DATE '2026-02-18', 0);

INSERT INTO planning_goals (id, title, target_amount, current_amount, target_date, notes, complete)
VALUES ('g1', 'Reserva de emergencia', 30000.00, 12400.00, DATE '2026-12-20', '6 meses de custo fixo', 0);

INSERT INTO retention_settings (id, trash_retention_days, audit_retention_days)
VALUES (1, 30, 180);

INSERT INTO app_preferences (
  id,
  default_bill_category,
  default_bill_recurring,
  default_bill_due_day,
  default_income_category,
  default_income_recurring,
  default_income_received_day,
  default_dashboard_mode,
  default_dashboard_month_comparison_offset
)
VALUES (1, 'Moradia', 0, 5, 'Trabalho', 0, 1, 'month', 1);

