CREATE TABLE spending_goals (
  id VARCHAR2(36 CHAR) NOT NULL,
  title VARCHAR2(180 CHAR) NOT NULL,
  limit_amount NUMBER(15,2) NOT NULL,
  category VARCHAR2(120 CHAR) NOT NULL,
  schedule VARCHAR2(20 CHAR) NOT NULL,
  start_month VARCHAR2(7 CHAR),
  start_date DATE,
  end_date DATE,
  active NUMBER(1) DEFAULT 1 NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_spending_goals PRIMARY KEY (id),
  CONSTRAINT ck_spending_limit_positive CHECK (limit_amount > 0),
  CONSTRAINT ck_spending_schedule CHECK (schedule IN ('monthly', 'custom')),
  CONSTRAINT ck_spending_active_bool CHECK (active IN (0, 1))
);

CREATE INDEX idx_spending_category ON spending_goals (category);

CREATE TABLE trash_items (
  id VARCHAR2(36 CHAR) NOT NULL,
  entity_type VARCHAR2(60 CHAR) NOT NULL,
  entity_id VARCHAR2(120 CHAR) NOT NULL,
  label VARCHAR2(255 CHAR) NOT NULL,
  payload CLOB NOT NULL,
  deleted_at TIMESTAMP WITH TIME ZONE NOT NULL,
  purge_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT pk_trash_items PRIMARY KEY (id)
);

CREATE INDEX idx_trash_purge_at ON trash_items (purge_at);
CREATE INDEX idx_trash_entity ON trash_items (entity_type, entity_id);

INSERT INTO spending_goals (id, title, limit_amount, category, schedule, start_month, active)
VALUES ('s1', 'Limite mensal essencial', 4200.00, 'ALL', 'monthly', '2026-01', 1);

INSERT INTO spending_goals (id, title, limit_amount, category, schedule, start_date, end_date, active)
VALUES ('s2', 'Controle alimentacao trimestre', 3200.00, 'Alimentacao', 'custom', DATE '2026-02-01', DATE '2026-04-30', 1);

