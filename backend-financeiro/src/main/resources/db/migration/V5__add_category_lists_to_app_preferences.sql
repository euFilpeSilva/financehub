ALTER TABLE app_preferences ADD (
  bill_categories VARCHAR2(2000 CHAR) DEFAULT 'Moradia|Alimentacao|Utilidades|Saude|Transporte|Educacao|Lazer|Outros' NOT NULL,
  income_categories VARCHAR2(2000 CHAR) DEFAULT 'Trabalho|Extra|Investimentos|Reembolso|Outros' NOT NULL
);

UPDATE app_preferences
SET
  bill_categories = 'Moradia|Alimentacao|Utilidades|Saude|Transporte|Educacao|Lazer|Outros',
  income_categories = 'Trabalho|Extra|Investimentos|Reembolso|Outros'
WHERE bill_categories IS NULL OR income_categories IS NULL;
