# Requisitos Funcionais e Não Funcionais - Finance Hub

Ultima revisao: 2026-03-05

---

## 1. Requisitos Funcionais (RF)

### 1.1 RF-001: Gerenciamento de Contas (Saídas/Bills)
- **Descrição**: Sistema deve permitir cadastro, edição, exclusão e listagem de contas (saídas de recursos).
- **Atores**: Usuário
- **Pré-condições**: Usuário autenticado
- **Fluxo principal**:
  1. Usuário acessa `/contas`
  2. Clica em "Nova Conta" e preenche formulário
  3. Campos: descrição, categoria, valor (mín. 0.01), data de vencimento, marcador de recorrência
  4. Sistema salva na base e exibe toast de sucesso
  5. Conta aparece em listagem e dashboard
- **Regras de negócio**:
  - Novas contas entram com `paid=false` (exceto importação OFX de débito)
  - Exclusão move para lixeira (não elimina imediatamente)
  - Recorrência gera contas automaticamente no mês (não duplica)
- **Critérios de aceitação**:
  - ✅ CRUD completo
  - ✅ Validação de campos obrigatórios
  - ✅ Confirmação antes de exclusão
  - ✅ Auditoria de criação/alteração/exclusão

### 1.2 RF-002: Gerenciamento de Entradas (Incomes)
- **Descrição**: Sistema deve permitir cadastro, edição, exclusão e listagem de entradas (receitas).
- **Atores**: Usuário
- **Fluxo principal**: Similar a RF-001, mas com campos: origem, categoria, valor, data de recebimento
- **Regras de negócio**:
  - Entradas podem ser marcadas como transferência interna (não impactam análise)
  - Exclusão move para lixeira
- **Critérios de aceitação**:
  - ✅ CRUD completo
  - ✅ Filtros por origem, categoria, período
  - ✅ Ordenação por coluna
  - ✅ Marcação opcional de transferência interna

### 1.3 RF-003: Metas de Planejamento (Planning Goals)
- **Descrição**: Sistema deve permitir definir, acompanhar e medir progresso de metas de médio/longo prazo.
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário acessa `/planejamento`
  2. Define meta com título, valor alvo, valor atual, data alvo
  3. Sistema calcula automaticamente `complete = currentAmount >= targetAmount`
  4. Usuário pode atualizar valor acumulado em tempo real
- **Critérios de aceitação**:
  - ✅ Cálculo automático de status de conclusão
  - ✅ Progresso visual em percentual
  - ✅ Exclusão com auditoria
  - ✅ Filtros e ordenação

### 1.4 RF-004: Metas de Gasto (Spending Goals)
- **Descrição**: Sistema deve definir limites de gasto por categoria e período, com acompanhamento automático.
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário acessa dashboard e clica em "Metas de Gasto"
  2. Define meta: título, categoria (ou `ALL`), limite, período (mensal ou customizado)
  3. Sistema calcula automaticamente:
     - `spentAmount`: soma de bills da categoria no período (excluindo transferências internas)
     - `remainingAmount = limitAmount - spentAmount`
     - `usagePercent = spentAmount / limitAmount`
     - `onTrack = spentAmount <= limitAmount`
  4. Card de acompanhamento exibe status visual (cor verde/amarelo/vermelho)
- **Regras de negócio**:
  - Categoria `ALL` agrupa todos os gastos
  - Período mensal respeita mês global selecionado
  - Período customizado usa intervalo explícito
  - Transferências internas não contam no gasto
- **Critérios de aceitação**:
  - ✅ Cálculo correto de gasto do período
  - ✅ Status on-track/overbudget
  - ✅ Validação semântica de datas
  - ✅ Ativação/inativação de metas

### 1.5 RF-005: Dashboard Analítico
- **Descrição**: Sistema deve exibir visão consolidada de saúde financeira com indicadores chave.
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário acessa `/dashboard` (rota padrão)
  2. Dashboard carrega automaticamente ao iniciar aplicação
  3. Sistema exibe:
     - **Indicadores principales**: entradas, contas, saldo, planejamento, contas pagas/pendentes
     - **Série mensal**: evolução de entradas, gastos, economia mês a mês
     - **Comparador de períodos**: snapshot de 2 meses ou 2 intervalos customizados
     - **Metas de gasto**: acompanhamento visual de limites por categoria
- **Regras de negócio**:
  - Sempre exclui movimentos com `internalTransfer=true`
  - Saldo = Entradas - Contas (sem internas)
  - Economia = Entradas - Contas (alcançado/foco em reduções de gasto)
  - Melhor mês = maior economia; Pior mês = maior gasto
  - Comparador exibe deltas: `Mês B - Mês A` (negativo = piora, exceto gasto onde negativo = melhora)
- **Critérios de aceitação**:
  - ✅ Cálculos sem transferências internas
  - ✅ Série mensal agregada por `YYYY-MM`
  - ✅ Comparador com interpretação correta de sinais
  - ✅ Persistência de modo (mensal/intervalo) e offset de meses
  - ✅ Tooltips explicativos para cada card

### 1.6 RF-006: Filtro Global de Período
- **Descrição**: Sistema deve permitir usuário selecionar período (mês ou intervalo) que afeta todas as listagens.
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário clica no seletor de mês/intervalo em `GlobalFilterBarComponent` (topo da aplicação)
  2. Seleciona mês específico ou ativa intervalo customizado
  3. Todas as telas (`/contas`, `/entradas`, `/dashboard`) refiltrageam dados automaticamente
- **Regras de negócio**:
  - Sem intervalo customizado: filtra apenas o mês selecionado
  - Com intervalo: aplica corte por `dueDate` (bills) e `receivedAt` (incomes)
  - Seleção é persistida em estado da facade
- **Critérios de aceitação**:
  - ✅ Seletor de mês com datepicker
  - ✅ Modo intervalo customizado
  - ✅ Filtro propagado para todas as telas
  - ✅ Sem recarregamento de dados (apenas refiltragem local)

### 1.7 RF-007: Importação OFX
- **Descrição**: Sistema deve importar transações de arquivos OFX em lote (drag-and-drop, seleção de arquivos ou pasta).
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário acessa `/extratos`
  2. Arrasta um ou mais arquivos `.ofx` (ou seleciona via input)
  3. Sistema solicita confirmação com e-mail do titular (opcional) e CPF (opcional)
  4. Backend faz parse:
     - Lê transações de blocos `<STMTTRN>`
     - Valor negativo → `bill` com `paid=true`
     - Valor positivo → `income`
     - Data extraída de `<DTPOSTED>` (ignorando hora)
     - Descrição extraída de `<MEMO>` (sanitizada, máx 120 caracteres)
  5. Detecta duplicatas (chave: `data|valor|descrição`)
  6. Detecta transferências internas (heurística por CPF/nome ou matching pós-import)
  7. Gera auditoria com resumo
- **Validações**:
  - Arquivo obrigatório e não vazio
  - Extensão `.ofx`
  - Conteúdo deve conter `<OFX>`
  - Charset: UTF-8 ou ISO-8859-1 conforme header
- **Regras de negócio**:
  - Categoria inicial: `Extrato importado`
  - Detecta transferência interna se memo contém `PIX|TRANSFER|TED|DOC` + evidência de titularidade (CPF/nome)
  - Importa rendimentos de blocos `BALLIST/BAL` como entradas quando `VALUE > 0`
  - Cria conta técnica legado para transferências de corretora descontinuada (Easynvest/NuInvest)
  - Matching cruzado pós-import (mesmo valor, proximidade de data) pode auto-marcar como interna
  - Duplicados ignorados e contabilizados em resumo
- **Critérios de aceitação**:
  - ✅ Suporta múltiplos arquivos
  - ✅ Detecta duplicatas
  - ✅ Detecta transferências internas (heurística + matching)
  - ✅ Resumo de importação com contadores
  - ✅ Auditoria registrada

### 1.8 RF-008: Exportação OFX
- **Descrição**: Sistema deve exportar transações em formato OFX para período especificado.
- **Atores**: Usuário / Sistema Externo (via API)
- **Fluxo principal**:
  1. Usuário faz requisição `GET /api/v1/statements/export/ofx?startDate=...&endDate=...`
  2. Backend monta arquivo `<OFX>`:
     - Bills → `DEBIT` (valor negativo)
     - Incomes → `CREDIT` (valor positivo)
     - Data de cada transação respeitada
  3. Calcula `LEDGERBAL` (saldo final = soma algébrica)
  4. Retorna arquivo com nome `extrato_yyyymmdd_yyyymmdd.ofx`
- **Validações**:
  - `startDate` não pode ser > `endDate`
  - Parâmetros opcionais: `bankOrg`, `bankId`, `accountId`
- **Critérios de aceitação**:
  - ✅ Exporta no formato OFX válido
  - ✅ Sinais corretos (débito/crédito)
  - ✅ Saldo final correto
  - ✅ Auditoria registrada

### 1.9 RF-009: Transferências Internas
- **Descrição**: Sistema deve permitir marcar pares de conta+entrada como transferência interna (não afeta análise financeira).
- **Atores**: Usuário / Sistema (detecção automática)
- **Fluxo principal**:
  1. **Vinculação Manual**:
     - Usuário acessa ferramenta de transferências (ou via dashboard)
     - Seleciona uma bill e uma income do mesmo valor
     - Sistema marca ambas com `internalTransfer=true`
  2. **Detecção Automática**:
     - Backend analisa bills e incomes em lote
     - Critérios:
       - Mesmo valor (tolerância pequena)
       - Data proximidade (até `transferDetectionDays`)
       - Evidência de titularidade: CPF ou nome do titular no histórico
     - Sistema gera sugestões (pode auto-aplicar se score suficiente)
- **Regras de negócio**:
  - Movimentos internos não contam em entradas, gastos, saldo, comparador
  - Um registro não pode ser pareado com vários no mesmo lote
  - Score mínimo exigido para sugestão (evidência de titularidade obrigatória)
- **Critérios de aceitação**:
  - ✅ Vinculação manual via endpoint
  - ✅ Detecção automática com score
  - ✅ Markação correta (flag `internalTransfer`)
  - ✅ Exclusão de cálculos analíticos
  - ✅ Auditoria de linking

### 1.10 RF-010: Lixeira (Soft Delete)
- **Descrição**: Sistema deve permitir exclusão lógica com recuperação posterior ou purga permanente.
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário clica em excluir conta, entrada ou meta
  2. Sistema solicita confirmação
  3. Registro é movido para `trash_items` (com JSON da entidade + timestamp de exclusão)
  4. Usuário pode acessar `/lixeira` para:
     - **Restaurar**: retorna item à tabela original
     - **Excluir permanentemente**: remove de trash_items (purga)
  5. Limpeza automática quando `purge_at` vence
- **Regras de negócio**:
  - Exclusão nunca é imediata (sempre lógica)
  - `purge_at` calculada com base em `trashRetentionDays` (configurável em `/configuracoes`)
  - Restauração reidrata JSON serializado
  - Auditoria registra operação (delete, restore, purge)
- **Critérios de aceitação**:
  - ✅ Soft delete para bills, incomes, planning goals, spending goals
  - ✅ Restauração funcional
  - ✅ Purga manual e automática
  - ✅ Filtros em lixeira (por tipo, período)
  - ✅ Auditoria completa

### 1.11 RF-011: Auditoria
- **Descrição**: Sistema deve registrar todas as operações críticas para rastreabilidade e conformidade.
- **Atores**: Sistema (automático)
- **Fluxo principal**:
  1. Usuário realiza operação (criar, editar, excluir, restaurar, etc.)
  2. Backend dispara evento de auditoria via `AuditPort`
  3. Evento é persistido em `audit_events` com:
     - Timestamp (`auditedAt`)
     - Tipo de entidade (`entityType`: bill, income, planning-goal, spending-goal, etc.)
     - Ação (`action`: create, update, delete, restore, purge, toggle-status, export, link-internal)
     - ID da entidade
     - Detalhes (valor anterior → novo para updates, valor monetário quando aplicável)
     - Usuário que realizou (se aplicável)
- **Regras de negócio**:
  - Alterações de configuração registram campo a campo: `"campo: valor_anterior → valor_novo"`
  - Alterações de preferências registram listas de categorias e datas
  - Retencao controlada por `auditRetentionDays` (configurável em `/configuracoes`)
  - Limpeza automática via rotina de retenção
- **Critérios de aceitação**:
  - ✅ Registro de todas as operações CRUD
  - ✅ Detalhes suficientes para reconstrução
  - ✅ Filtros em tela de auditoria (entidade, ação, período, valor, nome)
  - ✅ Retenção automática respeitada

### 1.12 RF-012: Configurações da Aplicação
- **Descrição**: Sistema deve permitir personalização de padrões, categorias, tema e retenção.
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário acessa `/configuracoes`
  2. **Aparência**: seleciona tema claro/escuro (persistido em `app_preferences`)
  3. **Padrões**:
     - Categoria padrão para bills e incomes
     - Recorrência padrão (sim/não)
     - Dia padrão de vencimento (1-31)
     - Modo padrão do dashboard (mensal/intervalo)
     - Offset padrão de comparação (meses a retroceder)
  4. **Categorias**:
     - Cadastra, edita, remove categorias de bills e incomes
     - Mínimo de 1 categoria em cada tipo
     - Sem duplicatas (case-insensitive)
     - Se remover categoria padrão, sistema escolhe outra automaticamente
  5. **Retenção**:
     - `trashRetentionDays`: dias até limpeza automática de lixeira (1-3650)
     - `auditRetentionDays`: dias até limpeza automática de auditoria (1-3650)
     - Botão "Limpar agora" para executar retenção manual
- **Validações**:
  - Categoria não vazia, máx 120 caracteres, unique
  - Dia padrão 1-31
  - Offset 1-12
  - Retenção 1-3650
- **Critérios de aceitação**:
  - ✅ Persistência de todas as configurações no backend
  - ✅ Validação semântica
  - ✅ Tema refletido imediatamente em toda aplicação
  - ✅ Limpeza de retenção via endpoint
  - ✅ Auditoria de alterações de configuração

### 1.13 RF-013: Filtros e Ordenação em Listagens
- **Descrição**: Cada tela de listagem (contas, entradas, planejamento, lixeira, auditoria) deve suportar filtros e ordenação por coluna.
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário acessa tela de listagem
  2. Filtros (locais à tela):
     - **Bills**: busca descrição, categoria, status (pago/pendente), recorrência, intervalo de valor
     - **Incomes**: busca origem, categoria, recorrência, intervalo de valor
     - **Planning Goals**: busca título, status, intervalo de data alvo
     - **Audit**: busca entidade, ação, período, intervalo de valor, nome
     - **Trash**: busca nome, tipo, período de exclusão
  3. Ordenação por clique em coluna header (alterna asc/desc)
  4. Filtros + ordenação aplicados localmente (dados já em memória)
- **Critérios de aceitação**:
  - ✅ Filtros responsivos (atualizam listagem em tempo real)
  - ✅ Ordenação bidirecional
  - ✅ Persistência de estado de coluna ordenada
  - ✅ Modo lista (tabela) e modo grade (cards) mantêm filtros

### 1.14 RF-014: Visualizações Alternativas (Lista e Grade)
- **Descrição**: Listagens devem suportar exibição em modo tabela (lista) e modo cards (grade).
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário clica em ícone (lista/grade) em tela de listagem
  2. Sistema alterna visualização
  3. Filtros e ordenação são mantidos
  4. Layout responsivo adapta-se ao tamanho da tela
- **Critérios de aceitação**:
  - ✅ Modo lista com cabeçalho sticky
  - ✅ Modo grade com cards informativos
  - ✅ Persistência de preferência de visualização (opcional)
  - ✅ Responsividade (mobile-first com TailwindCSS)

### 1.15 RF-015: Confirmação de Ações Críticas
- **Descrição**: Operações críticas (criar, editar com consequências, excluir) devem pedir confirmação explícita.
- **Atores**: Usuário
- **Fluxo principal**:
  1. Usuário clica em ação crítica (ex: "Excluir Conta")
  2. Modal `ConfirmDialogComponent` aparece com pergunta clara
  3. Usuário confirma ou cancela
  4. Se confirmar: operação prossegue; senão: modal fecha sem efeito
- **Regras de negócio**:
  - Exclusões usam tom `danger` (vermelho)
  - Modal exibe descrição clara da ação
- **Critérios de aceitação**:
  - ✅ Modal bloqueante
  - ✅ Confirmação explícita obrigatória
  - ✅ Cancelamento desfaz operação

### 1.16 RF-016: Feedback Visual (Toasts)
- **Descrição**: Sistema deve exibir feedback de sucesso, erro ou informação via toast.
- **Atores**: Sistema
- **Fluxo principal**:
  1. Usuário completa operação (CRUD, ajuste, import)
  2. Backend responde com sucesso ou erro
  3. Frontend emite `operationNotice` sinal na facade
  4. `AppComponent` observa e chama `ToastService`
  5. Toast aparece no canto superior direito (ou conforme config)
  6. Fecha automaticamente após 4.5 segundos ou clique do usuário
- **Critérios de aceitação**:
  - ✅ Mensagens claras em português
  - ✅ Ícones apropriados (✓ sucesso, ✗ erro, ℹ informação)
  - ✅ Fechamento automático ou manual
  - ✅ Stack de múltiplos toasts visível

### 1.17 RF-017: Autenticação Inicial (Planejado for Future)
- **Descrição**: Sistema deve suportar autenticação básica (placeholder para futuro).
- **Status**: Não implementado; atualmente sem autentificação.

### 1.18 RF-018: Responsividade Mobile
- **Descrição**: Todas as telas devem ser totalmente funcionais em dispositivos móveis (smartphone, tablet).
- **Atores**: Usuário em dispositivo móvel
- **Fluxo principal**:
  1. Usuário acessa aplicação em navegador mobile
  2. Layout adapta-se a tela pequena
  3. Componentes mobile-first (TailwindCSS breakpoints)
  4. Inputs, botões, filtros funcionam sem adaptação manual
- **Critérios de aceitação**:
  - ✅ Breakpoints mobile (sm, md, lg, xl, 2xl)
  - ✅ Menu responsivo (drawer lateral em mobile)
  - ✅ Tabelas scrolláveis horizontalmente
  - ✅ Formulários ajustados

---

## 2. Requisitos Não Funcionais (RNF)

### 2.1 RNF-001: Performance - Tempo de Resposta da API
- **Descrição**: APIs REST devem responder em tempo aceitável
- **Métrica**:
  - Endpoints `GET`: < 1 segundo para operações sem agregação
  - Endpoints `GET` com agregação (analytics): < 3 segundos
  - Endpoints `POST/PUT/DELETE`: < 500ms
- **Implementação**: Spring Boot 3.3.x com caching, índices no Oracle, lazy loading via Spring Data
- **Validação**: Testes de carga com JMeter ou Gatling (não implementado)

### 2.2 RNF-002: Performance - Tempo de Carregamento Frontend
- **Descrição**: Aplicação Angular deve carregar rápido e ser responsiva
- **Métrica**:
  - Initial Load (sem cache): < 3 segundos
  - Navegação entre rotas: < 500ms
  - Renderização de listagens com 1000+ itens: < 1 segundo
- **Implementação**:
  - Standalone components (sem NgModule overhead)
  - Lazy loading de rotas
  - Change detection OnPush
  - Signals para reatividade eficiente
  - Bundle minificado e comprimido (gzip)
- **Validação**: Chrome DevTools Lighthouse, npm run build (produção)

### 2.3 RNF-003: Escalabilidade - Suporte a Múltiplos Usuários
- **Descrição**: Sistema deve suportar múltiplos usuários simultâneos (planejado para futuro com autenticação)
- **Métrica**: 
  - Atualmente: single-user (foco local)
  - Future-ready: separação por identificador de usuário em queries
- **Implementação**: Schema Oracle preparado para multi-tenancy (futuro)

### 2.4 RNF-004: Disponibilidade - Uptime
- **Descrição**: Sistema em produção deve estar disponível como esperado
- **Métrica**:
  - Alvo: 99% (compatível com SLA típico de produto)
  - Atualmente: ambiente development local
- **Implementação**: Docker Compose com health checks, reinício automático de containers

### 2.5 RNF-005: Segurança - Credenciais Não Expostas
- **Descrição**: Senhas, tokens e dados sensíveis não devem ser versionados em Git
- **Métrica**:
  - ✅ .env não versionado
  - ✅ Valores hardcoded removidos
  - ✅ Exemplos (.env.example) usam CHANGE_ME_* placeholders
- **Implementação**:
  - .gitignore excludes .env, backups, build artifacts
  - docker-compose.yml usa variable substitution `${VAR:?}`
  - Spring Boot properties não têm fallback de password
  - GitHub Actions (futuro) não expõe secrets

### 2.6 RNF-006: Segurança - Sanitização de Entrada
- **Descrição**: Dados de entrada devem ser normalizados e validados
- **Métrica**:
  - OFX import: sanitização de MEMO, remoção de caracteres inválidos
  - Descrições de contas: truncamento a 120 caracteres
- **Implementação**:
  - Bean Validation (@NotBlank, @NotNull, @Min, @Max)
  - Sanitização em service layer (OFX parser)
  - Angular form validation (frontend + backend)

### 2.7 RNF-007: Segurança - Auditoria e Rastreabilidade
- **Descrição**: Todas as operações críticas devem ser registradas para compliance
- **Métrica**:
  - 100% de operações CRUD auditadas
  - Retenção de auditoria configurável (1-3650 dias)
- **Implementação**:
  - `GovernanceService` implementa `AuditPort`
  - `audit_events` tabela com timestamp, entidade, ação, detalhes
  - Filtros de auditoria em `/auditoria`

### 2.8 RNF-008: Integridade de Dados - Constraints no Banco
- **Descrição**: Banco de dados deve enforçar integridade referencial e de negócio
- **Métrica**:
  - Valores monetários > 0
  - Datas válidas
  - Foreign keys obrigatórias
  - Flags booleanas (0,1)
- **Implementação**:
  - Constraints SQL em `V1__init_schema.sql` (check, not null, unique, fk)
  - Validação JPA no backend (não confiar apenas no Oracle)

### 2.9 RNF-009: Persistência - Versionamento de Schema
- **Descrição**: Mudanças no schema devem ser versionadas e reversíveis
- **Métrica**:
  - Todas as mudanças em migrations Flyway (V1, V2, V3, ...)
  - Nenhuma mudança ad-hoc no banco
- **Implementação**:
  - Flyway automático ao startup (`ddl-auto: validate`)
  - Migrações em `src/main/resources/db/migration`
  - Histórico de versões documentado

### 2.10 RNF-010: Backup e Recuperação
- **Descrição**: Dados devem estar protegidos contra perda
- **Métrica**:
  - Volume Docker `oracle_data` persistido em filesystem host
  - Backups manuais com scripts PowerShell (dentro do projeto)
  - Capacidade de restore de backup
- **Implementação**:
  - `scripts/backup-volume.ps1`: tar + sha256 checksum
  - `scripts/restore-volume.ps1`: verificação de integridade + restore
  - Armazenamento em `backups/` (não versionado)

### 2.11 RNF-011: Manutenibilidade - Código Limpo e Documentado
- **Descrição**: Codebase deve ser fácil de entender e manter
- **Métrica**:
  - Código segue convenções de estilo
  - Documentação em comentários para lógica complexa
  - Nomes descritivos (funciones, classes, variáveis)
- **Implementação**:
  - Backend: Java com padrão domain/application/infrastructure
  - Frontend: Angular standalone components com Type Safety
  - Documentação: README.md, DOCUMENTACAO.md, REGRAS_NEGOCIO_APLICACAO.md, MANUAL_OPERACAO_SISTEMA.md
  - Code review em Git (future nice-to-have)

### 2.12 RNF-012: Portabilidade - Containerização
- **Descrição**: Aplicação deve rodar identicamente em qualquer máquina com Docker
- **Métrica**:
  - Docker Compose com 3 serviços (Oracle, Backend, Frontend)
  - Multi-stage builds (reduz imagem)
  - Sem dependências do host (exceto Docker engine)
- **Implementação**:
  - `docker-compose.yml` coordena serviços
  - `Dockerfile` para backend (Maven multi-stage)
  - `Dockerfile` para frontend (Node→Nginx multi-stage)
  - Health checks configurados
  - Named volume para persistência

### 2.13 RNF-013: Compatibilidade - Navegadores Modernos
- **Descrição**: Frontend deve rodar em navegadores atuais
- **Métrica**:
  - Chrome, Firefox, Safari (últimas 2 versões)
  - Mobile browsers (Chrome, Safari, Firefox mobile)
- **Implementação**:
  - Angular 19 compila para ES 2022+
  - TailwindCSS suporta todos os navegadores modernos
  - Sem APIs deprecated

### 2.14 RNF-014: Internacionalização (I18N) - Planejado para Futuro
- **Descrição**: Aplicação suporta múltiplos idiomas
- **Métrica**: Atualmente português (Brasil) apenas
- **Future**: `@ngx-translate` ou i18n Angular nativo

### 2.15 RNF-015: Acessibilidade (A11Y) - Planejado para Futuro
- **Descrição**: Aplicação deve ser utilizável por pessoas com deficiências
- **Métrica**: 
  - WCAG 2.1 AA (futuro)
  - Atualmente foco em cores contrastadas, labels em inputs
- **Implementação** (futuro):
  - ARIA labels
  - Navegação por teclado
  - Screen reader support

### 2.16 RNF-016: Testabilidade
- **Descrição**: Código deve ser facilmente testável
- **Métrica**:
  - Backend: testes unitários com JUnit 5 + Mockito
  - Frontend: testes com Jasmine (opcional, atualmente não implementado)
  - Database: testes com H2 in-memory
- **Implementação**:
  - Backend: `mvn test` executa suite completa
  - Spring Boot test profile com H2
  - Injeção de dependências para mocks

### 2.17 RNF-017: Observabilidade
- **Descrição**: Sistema deve ser monitorável em produção
- **Métrica**:
  - Atuais: logs estruturados no console Docker
  - Health check: `GET /actuator/health` (Spring Boot Actuator)
- **Implementation**:
  - Spring Boot Actuator endpoints (health, info)
  - Logs estruturados (info, warn, error)
  - Docker logs centralizáveis

### 2.18 RNF-018: Conformidade Regulatória - LGPD (Planejado)
- **Descrição**: Sistema deve respeitar Lei Geral de Proteção de Dados
- **Métrica**:
  - Retenção configurável de dados
  - Possibilidade de exclusão permanente (GDPR right to be forgotten)
- **Implementação**:
  - `TrashService` com expurgo automático e manual
  - Auditoria de operações de dados sensíveis
  - Documentação de privacidade (future)

---

## 3. Mapeamento RF ↔ Telas

| Tela | Rota | RF Associados |
|------|------|---------------|
| Dashboard | `/dashboard` | RF-005, RF-006, RF-004 |
| Contas | `/contas` | RF-001, RF-006, RF-013, RF-014, RF-015, RF-016 |
| Entradas | `/entradas` | RF-002, RF-006, RF-013, RF-014, RF-015, RF-016 |
| Planejamento | `/planejamento` | RF-003, RF-013, RF-014, RF-015, RF-016 |
| Extratos | `/extratos` | RF-007, RF-009, RF-016 |
| Lixeira | `/lixeira` | RF-010, RF-013, RF-014, RF-015 |
| Auditoria | `/auditoria` | RF-011, RF-013, RF-014 |
| Configurações | `/configuracoes` | RF-012, RF-016 |

---

## 4. Mapeamento RNF ↔ Componentes Técnicos

| RNF | Tecnologia | Componente |
|-----|-----------|-----------|
| RNF-001 (API Response) | Spring Boot 3.3.x | REST Controllers, Service Layer, JPA Repositories |
| RNF-002 (Frontend Load) | Angular 19 Standalone | Lazy Loading, OnPush, Signals, Tree-shaking |
| RNF-004 (Availability) | Docker Compose | Health checks, auto-restart policies |
| RNF-005 (Credentials) | .env + Docker | Variable substitution, .gitignore |
| RNF-006 (Sanitization) | Spring Validation + Custom Sanitizers | Bean Validation, OFX Parser |
| RNF-007 (Audit) | GovernanceService + audit_events table | AuditPort impl, database triggers (future) |
| RNF-008 (DB Integrity) | Oracle Free 23c | Constraints, Check clauses, Foreign keys |
| RNF-009 (Schema Versioning) | Flyway | V1__init_schema.sql, V2__*, ... |
| RNF-010 (Backup) | Docker Volume + PowerShell | backup-volume.ps1, restore-volume.ps1 |
| RNF-011 (Code Quality) | Git, documentation | README, REGRAS_NEGOCIO_APLICACAO.md |
| RNF-012 (Containerization) | Docker Compose, Multi-stage builds | Orchestration, volume management |
| RNF-013 (Browser Compatibility) | Angular 19 + TailwindCSS | ES2022+ transpilation, CSS compatibility |
| RNF-016 (Testability) | JUnit 5, Mockito, H2 | Spring Boot Test, TestContainer (future) |
| RNF-017 (Observability) | Spring Boot Actuator, Docker logs | health endpoint, container logging |

---

## 5. Critérios de Aceitação Gerais

Toda feature deve atender:

1. **Funcionalidade**:
   - ✅ Comportamento conforme RF especificado
   - ✅ Casos de sucesso e erro tratados
   - ✅ Validações aplicadas frontend + backend

2. **Integração**:
   - ✅ API REST responde com status HTTP correto
   - ✅ Frontend consome corretamente
   - ✅ Feedback visual (toast) apropriado
   - ✅ Auditoria registrada se operação crítica

3. **Performance**:
   - ✅ Não viola RNF-001, RNF-002 (tempo de resposta)
   - ✅ Sem queries N+1
   - ✅ Sem bloqueios de renderização

4. **Qualidade**:
   - ✅ Código segue convenções (naming, structure)
   - ✅ Sem código duplicado
   - ✅ Testes unitários (backend)
   - ✅ Nenhum console.error ou warning (frontend)

5. **Segurança**:
   - ✅ Input sanitizado
   - ✅ Validações no backend (nunca confiar em frontend)
   - ✅ Sem dados sensíveis em logs/console

6. **Documentação**:
   - ✅ REGRAS_NEGOCIO_APLICACAO.md atualizado (se nova regra)
   - ✅ Comentários em código complexo
   - ✅ Commit message descritiva em Git

---

## 6. Histórico de Revisões

| Data | Autor | Alteração |
|------|-------|-----------|
| 2026-03-01 | Agent | Criação inicial: 18 RF + 18 RNF estruturados |

---

**Status**: Documento vivo. Revisar e atualizar conforme novas features são implementadas.
