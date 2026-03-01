# Status e Proximos Passos - Finance Hub

## Escopo

Este documento registra o estado técnico atual do Finance Hub e os próximos passos recomendados para continuidade do desenvolvimento entre frontend e backend.

Data de referência: 27/02/2026

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

### Infra/execução
- Persistência atual: in-memory (mock técnico para evolução inicial).
- CORS configurado para `http://localhost:4200`.
- Java configurado para 17 no `pom.xml` (compatível com ambiente atual).
- Teste de contexto Spring (`mvn test`) executando com sucesso.

## 4) Decisões importantes já tomadas

- Não usar microserviços neste momento.
- Evoluir primeiro como monólito modular para acelerar entrega e reduzir custo operacional.
- Preparar separação por contexto de domínio desde agora para facilitar extração futura, se necessário.

## 5) Próximos passos recomendados (ordem sugerida)

1. Integração frontend-backend
- Criar `HttpFinanceGateway` no Angular.
- Trocar injeção do gateway mock por gateway HTTP.
- Configurar `environment.ts` com `apiBaseUrl`.

2. Persistência real no backend
- Adicionar Spring Data JPA + PostgreSQL.
- Criar entidades JPA, migrations e repositórios reais.
- Implementar soft delete/lixeira e retenção em banco.

3. Segurança
- Adicionar Spring Security + JWT.
- Criar fluxo de autenticação e proteção de endpoints.
- Definir modelo de permissões (ex.: usuário comum/admin).

4. Qualidade e testes
- Backend: testes unitários de serviço + testes de integração dos controllers.
- Frontend: testes de facade/componentes críticos.
- Padronizar lint/format e pipeline CI.

5. Evoluções de domínio
- Completar endpoints para recursos ainda somente no frontend (ex.: spending goals e ciclo completo de lixeira/auditoria, caso necessário).
- Refinar regras de recorrência (geração mensal controlada e idempotente).
- Melhorar analytics comparativos e histórico consolidado.

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
