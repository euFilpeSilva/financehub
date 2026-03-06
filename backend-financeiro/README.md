# Backend Financeiro (Spring Boot)

Ultima revisao: 2026-03-05

API em monolito modular para gestao financeira, agora com persistencia Oracle via JPA e migrations Flyway.

Documentacao recomendada:
- `../DOCUMENTACAO.md`
- `../MANUAL_OPERACAO_SISTEMA.md`

## Stack
- Java 17
- Spring Boot 3.3.x
- Spring Web + Validation + Actuator
- Spring Data JPA
- Flyway
- Oracle Database Free 23c (container)

## Estrutura
```text
src/main/java/com/financehub/backend
  config/
  shared/
    api/
    application/port/
  modules/
    bills/
      api/ application/ domain/ infrastructure/
    incomes/
      api/ application/ domain/ infrastructure/
    planning/
      api/ application/ domain/ infrastructure/
    governance/
      api/ application/ domain/ infrastructure/
    analytics/
      api/ application/
```

## Persistencia
- Schema versionado em `src/main/resources/db/migration`.
- Migration inicial: `V1__init_schema.sql`.
- `spring.jpa.hibernate.ddl-auto=validate` para garantir alinhamento com migrations.

## Subir stack completa com Docker Compose (Oracle + Backend + Frontend)
No diretorio raiz do projeto (`financehub`):

```bash
cp .env.example .env
docker compose up --build
```

Antes de subir, edite o `.env` e defina senhas fortes para:
- `ORACLE_PASSWORD`
- `APP_USER_PASSWORD`

Servicos:
- Oracle: `localhost:1521` (service name `FREEPDB1`)
- Backend: `localhost:8080`
- Frontend: `localhost:4200`

Volume persistente:
- `oracle_data` (mantem dados mesmo apos restart dos containers)

## Rodar backend localmente (fora de container)
Necessita Oracle ativo e variaveis abaixo (opcional se usar defaults de `application.yml`):

```bash
DB_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1
DB_USERNAME=financehub
DB_PASSWORD=<defina-uma-senha-forte>
```

Execucao:

```bash
mvn spring-boot:run
```

## Endpoints base
- `GET/POST/PUT/DELETE /api/v1/bills`
- `GET/POST/PUT/DELETE /api/v1/incomes`
- `GET/POST/PUT/DELETE /api/v1/planning-goals`
- `GET/POST/PUT/DELETE /api/v1/spending-goals`
- `GET /api/v1/governance/audit-events`
- `GET /api/v1/governance/trash-items`
- `POST /api/v1/governance/trash-items/{trashId}/restore`
- `DELETE /api/v1/governance/trash-items/{trashId}`
- `GET/PUT /api/v1/governance/retention-settings`
- `GET/PUT /api/v1/governance/app-preferences`
- `POST /api/v1/governance/retention-cleanup`
- `GET /api/v1/analytics/dashboard-summary?startDate=2026-02-01&endDate=2026-02-28`
- `GET /api/v1/statements/export/ofx?startDate=2026-02-01&endDate=2026-02-28`
- `POST /api/v1/statements/import/ofx`
- `POST /api/v1/transfers/internal/link`
- `POST /api/v1/transfers/internal/detect`

## Regra de analise para transferencias internas
- `bills.internalTransfer=true` e `incomes.internalTransfer=true` nao entram nos totais de receitas/despesas do dashboard.
- Use `POST /api/v1/transfers/internal/link` para vincular uma saida e uma entrada da mesma titularidade.
- Use `POST /api/v1/transfers/internal/detect` para sugerir (ou aplicar automaticamente) vinculacoes por valor, data e sinais de titularidade.
