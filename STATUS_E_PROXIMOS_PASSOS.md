# Status e Proximos Passos - Finance Hub

Ultima revisao: 2026-03-05

## Escopo

Este documento registra o estado técnico atual do Finance Hub e os próximos passos recomendados para continuidade do desenvolvimento entre frontend e backend.

## 1) Estado atual do projeto

Existem dois projetos no workspace:

- Frontend Angular: `gestao-financeira`
- Backend Spring Boot: `backend-financeiro`

## 2) O que já está implementado no frontend (`gestao-financeira`)

### Funcionalidades principais
- Dashboard com indicadores, comparador de períodos e série mensal.
- Gestão de saídas (contas): criar, editar, excluir, marcar pago/pendente, recorrência.
- Gestão de entradas: criar, editar, excluir, recorrência.
- Planejamento: metas financeiras e metas de gasto.
- Governança:
  - Lixeira com restauração/exclusão permanente.
  - Auditoria de eventos com filtros (período, nome, valor).
  - Configurações de retenção e preferências da aplicação.
- Tema claro/escuro.
- Máscara monetária em campos de valor (BRL).

### Melhorias arquiteturais já aplicadas
- Refatoração do `FinanceFacade` para reduzir recarga total em mutações.
- Centralização de constantes compartilhadas (categorias, navegação, labels).
- Correção de tipagem de auditoria para entidades de configurações/preferências.
- Header de “cockpit financeiro” visível apenas no dashboard.
- Ajustes de layout e espaçamento no tema escuro.

### Situação de qualidade
- Build do frontend passa com sucesso.
- Cobertura de testes ainda baixa (praticamente só teste base do AppComponent).

## 3) O que já está implementado no backend (`backend-financeiro`)

### Arquitetura escolhida
- Monólito modular (recomendado para fase atual do produto).
- Organização por módulos e camadas:
  - `api`
  - `application`
  - `domain`
  - `infrastructure` (quando aplicável)

### Módulos criados
- `bills`
- `incomes`
- `planning`
- `governance`
- `analytics`

### Endpoints base disponíveis (`/api/v1`)
- `GET/POST/PUT/DELETE /bills`
- `GET/POST/PUT/DELETE /incomes`
- `GET/POST/PUT/DELETE /planning-goals`
- `GET /governance/audit-events`
- `GET/PUT /governance/retention-settings`
- `GET/PUT /governance/app-preferences`
- `GET /analytics/dashboard-summary?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`

### Infra/execucao
- Persistencia Oracle ativa via JPA + Flyway.
- CORS configurado para `http://localhost:4200`.
- Java 17 no `pom.xml`.
- Build e compilacao do backend validados localmente.

## 4) Decisões importantes já tomadas

- Não usar microserviços neste momento.
- Evoluir primeiro como monólito modular para acelerar entrega e reduzir custo operacional.
- Preparar separação por contexto de domínio desde agora para facilitar extração futura, se necessário.

## 5) Próximos passos recomendados (ordem sugerida)

1. Robustez da importacao OFX
- Evoluir importacao para job assincrono com acompanhamento de processamento no backend (SSE/WebSocket).
- Adicionar endpoint de consulta por `jobId` para retomada de status apos refresh.

2. Qualidade e testes
- Backend: ampliar testes unitarios e de integracao (import OFX, transferencias internas, deduplicacao).
- Frontend: testes de componente/facade para fluxo de importacao com progresso global.

3. Seguranca
- Adicionar Spring Security + JWT.
- Criar fluxo de autenticação e proteção de endpoints.
- Definir modelo de permissões (ex.: usuário comum/admin).

4. Evolucoes de dominio
- Criar rotina de reclassificacao retroativa de transferencias para corretora legado (Easynvest/NuInvest).
- Ajustar conciliacao por conta para separar claramente conta origem x conta tecnica legado.
- Refinar regras de recorrencia (geracao mensal controlada e idempotente).

## 6) Como retomar rapidamente depois

### Frontend
```bash
cd gestao-financeira
npm install
npm run start
```

### Backend
```bash
cd backend-financeiro
mvn spring-boot:run
```

## 7) Observações finais

- O backend atual é uma base funcional e organizada para acelerar integração.
- Antes de produção, ainda faltam persistência real, segurança e testes mais robustos.
- A estrutura já está preparada para evolução incremental sem retrabalho grande.
