# Regras de Negocio da Aplicacao Finance Hub

## 1. Objetivo deste documento
Este documento centraliza as regras de negocio atualmente implementadas no sistema (frontend Angular + backend Spring Boot + Oracle).
Ele descreve:
- O que cada modulo faz
- Em que tela cada regra aparece
- Como cada regra funciona por baixo dos panos
- Validacoes, filtros, ordenacoes, auditoria, lixeira e importacao/exportacao OFX

## 2. Visao macro da arquitetura
Fluxo padrao de operacao:
1. Usuario interage na tela Angular.
2. Componente chama `FinanceFacade`.
3. `FinanceFacade` chama `FinanceGateway` (implementacao HTTP: `HttpFinanceGateway`).
4. Frontend chama API REST no backend (`/api/v1/...`).
5. Controller delega para Service.
6. Service aplica regras de negocio e persiste via repositorio JPA.
7. Flyway mantem o schema Oracle versionado.
8. Eventos de auditoria e itens da lixeira sao mantidos em tabelas dedicadas.

Observacao importante:
- O arquivo de mock (`MockFinanceGateway`) ainda existe no codigo, mas a aplicacao esta configurada para usar o gateway HTTP real em `app.config.ts`.

## 3. Mapa de telas e responsabilidade
Rotas principais:
- `/dashboard`: cockpit financeiro, comparacoes, serie mensal, metas de gasto
- `/contas`: cadastro e listagem de saidas (contas)
- `/entradas`: cadastro e listagem de entradas
- `/planejamento`: metas financeiras de medio/longo prazo
- `/extratos`: importacao OFX (drag and drop, arquivos e pasta)
- `/lixeira`: restauracao ou exclusao permanente de itens removidos
- `/auditoria`: historico de eventos auditaveis
- `/configuracoes`: tema, categorias, padroes e retencao

## 4. Regras transversais (valem para varios modulos)

### 4.1 Confirmacao de acao critica
Regras:
- Inclusao, edicao e exclusao exigem confirmacao explicita em modal.
- Exclusoes usam tom de perigo (`tone: danger`).

Como funciona:
- Telas chamam `ConfirmDialogService.confirm(...)`.
- O modal global `app-confirm-dialog` resolve promessa booleana.
- Se usuario cancelar, operacao nao segue.

### 4.2 Feedback visual de sucesso/erro (toasts)
Regras:
- Toda operacao via facade pode emitir aviso de sucesso ou erro.
- Toast fecha automaticamente apos 4.5 segundos.

Como funciona:
- `FinanceFacade` emite `operationNotice`.
- `AppComponent` observa esse sinal e chama `ToastService.success/error`.
- Container global `app-toast-container` exibe as mensagens.

### 4.3 Filtro global de periodo (Dashboard)
Regras:
- Filtro global define `mes selecionado` e opcionalmente intervalo de datas.
- Sem intervalo customizado, listagens filtradas usam apenas o mes.
- Com intervalo customizado, aplica corte por data.

Como funciona:
- `GlobalFilterBarComponent` atualiza `selectedMonth`, `rangeStart`, `rangeEnd` na facade.
- `filteredBills` e `filteredIncomes` usam `matchesMonthAndRange`.

### 4.4 Exclusao logica com lixeira
Regras:
- Excluir conta, entrada ou meta nao remove imediatamente de forma irreversivel.
- Registro e movido para `trash_items` com data de expurgo (`purge_at`).
- Usuario pode restaurar ou excluir permanentemente.

Como funciona:
- Services de dominio chamam `TrashService.moveToTrash(...)` antes de remover entidade original.
- Lixeira gerencia restauracao por tipo (`bill`, `income`, `planning-goal`, `spending-goal`).

### 4.5 Auditoria
Regras:
- Sistema audita criacao, alteracao, exclusao, restauracao, expurgo e eventos de extrato/transferencia/configuracao.
- Auditoria pode incluir valor monetario quando aplicavel.
- Alteracoes de configuracao registram campo alterado com `valor anterior -> valor novo`.

Como funciona:
- `GovernanceService` implementa `AuditPort.record(...)`.
- Services chamam `record` para eventos relevantes.
- Dados persistidos em `audit_events`.

### 4.6 Transferencias internas nao contam como gasto/receita final
Regra central:
- Itens marcados com `internalTransfer = true` sao excluidos de totais analiticos (entradas, gastos, saldo, pagos, pendentes e comparacoes).

Como funciona:
- Facade e AnalyticsService filtram itens internos antes de somar.
- Marcacao pode ser manual (`/transfers/internal/link`) ou automatica (detector).

## 5. Regras por dominio funcional

## 5.1 Saidas (Contas)
Tela:
- `/contas`

Campos e validacoes:
- `description`: obrigatorio, maximo 120 no frontend; backend exige nao vazio.
- `category`: obrigatorio.
- `amount`: obrigatorio, minimo 0.01.
- `dueDate`: obrigatorio.
- `recurring`: booleano.
- `paid`: status pago/pendente.

Regras de negocio:
- Novas contas entram com `paid=false` (exceto importacao OFX de debito, que entra pago).
- Botao de status alterna `paid`.
- Excluir move para lixeira e gera auditoria.
- Geracao de recorrentes cria contas no mes atual apenas se nao existir duplicata por descricao+categoria+mes.

Filtros de listagem:
- Busca por descricao
- Categoria
- Status (Pago/Pendente)
- Recorrencia (sim/nao)
- Periodo inicial/final

Ordenacao:
- Lista permite ordenar por coluna: conta, vencimento, valor, status.
- Direcao alterna asc/desc.

Visualizacao:
- Modo lista (tabela com cabecalho fixo/sticky)
- Modo grade (cards)

Endpoint:
- `GET/POST/PUT/DELETE /api/v1/bills`

## 5.2 Entradas
Tela:
- `/entradas`

Campos e validacoes:
- `source`: obrigatorio, maximo 120 no frontend; backend exige nao vazio.
- `category`: obrigatorio.
- `amount`: obrigatorio, minimo 0.01.
- `receivedAt`: obrigatorio.
- `recurring`: booleano.

Regras:
- Excluir move para lixeira e gera auditoria.
- Entradas podem ser marcadas internamente para nao influenciar analise de fluxo de caixa real.

Filtros:
- Busca por origem
- Categoria
- Recorrencia
- Periodo inicial/final
- Faixa de valor minimo/maximo

Ordenacao:
- Colunas: origem, recebimento, valor, recorrencia.

Visualizacao:
- Lista (cabecalho sticky)
- Grade

Endpoint:
- `GET/POST/PUT/DELETE /api/v1/incomes`

## 5.3 Metas de planejamento
Tela:
- `/planejamento`

Campos e validacoes:
- `title`: obrigatorio.
- `targetAmount`: minimo 0.01.
- `currentAmount`: minimo 0.
- `targetDate`: obrigatorio.
- `notes`: opcional.
- `complete`: booleano.

Regras:
- Ao editar, tela recalcula `complete` como `currentAmount >= targetAmount`.
- No modo grade, usuario pode atualizar valor acumulado direto, e facade recalcula status.
- Excluir move para lixeira e audita.

Filtros:
- Titulo
- Status (em andamento/concluida)
- Faixa de data alvo

Ordenacao:
- Colunas: meta, data alvo, valor alvo, valor atual, status.

Visualizacao:
- Lista (cabecalho sticky)
- Grade

Endpoint:
- `GET/POST/PUT/DELETE /api/v1/planning-goals`

## 5.4 Metas de gasto
Tela:
- Blocos dentro do `/dashboard`:
  - Formulario "Metas de gasto"
  - Card "Acompanhamento das metas"

Campos e validacoes:
- `title`: obrigatorio.
- `limitAmount`: minimo 0.01.
- `category`: obrigatorio (`ALL` ou categoria especifica).
- `schedule`: `monthly` ou `custom`.
- `monthly`: exige `startMonth`.
- `custom`: exige `startDate` e `endDate`; `endDate` nao pode ser menor que `startDate`.
- `active`: booleano.

Regras de calculo:
- Sistema calcula gasto no periodo da meta com base em saidas (`bills`), excluindo transferencias internas.
- Se categoria da meta != `ALL`, filtra apenas aquela categoria.
- Status da meta:
  - `onTrack=true` quando `spentAmount <= limitAmount`
  - `remainingAmount = limitAmount - spentAmount`
  - `usagePercent = spentAmount / limitAmount`

Regra de periodo da meta:
- `monthly`: usa mes selecionado globalmente, respeitando `startMonth` minimo.
- `custom`: usa intervalo explicito da meta.

Acoes:
- Criar, editar, ativar/inativar, excluir (com confirmacao).
- Exclusao move para lixeira.

Endpoint:
- `GET/POST/PUT/DELETE /api/v1/spending-goals`

## 5.5 Dashboard analitico
Tela:
- `/dashboard`

Cards e indicadores:
- Entradas do periodo
- Contas do periodo
- Saldo (entradas - contas)
- Planejamento (media de progresso de metas)
- Contas pagas e pendentes
- Entradas registradas
- Metas ativas
- Melhor mes para economizar
- Mes com maior gasto
- Serie mensal

Regras de calculo:
- Sempre exclui movimentos com `internalTransfer=true` de entradas/gastos/saldo.
- Serie mensal agrega por `YYYY-MM` usando datas de conta e entrada.
- Melhor mes: maior `savings`.
- Pior mes: maior `expenses`.

Comparador de periodos:
- Modo `Mes x mes`: compara snapshots de 2 meses.
- Modo `Intervalos customizados`: compara 2 faixas de datas.
- Retorna deltas de entrada, gasto e economia.

Persistencia de preferencia:
- Dashboard aplica modo padrao e offset configurados em preferencias da aplicacao no primeiro carregamento.

Observacao tecnica:
- Ha endpoint backend de resumo (`/analytics/dashboard-summary`), mas o frontend atual calcula indicadores localmente a partir das listas carregadas.

## 5.6 Importacao de extrato OFX
Tela:
- `/extratos`

UX e operacao:
- Upload por drag-and-drop, selecao de arquivos ou selecao de pasta.
- Aceita multiplos arquivos `.ofx`.
- Pede confirmacao antes de iniciar importacao.
- Exibe resumo da ultima importacao.

Entradas auxiliares:
- `ownerName` e `ownerCpf` (usados na deteccao de transferencia interna).

Validacoes do backend:
- Arquivo obrigatorio e nao vazio.
- Extensao deve ser `.ofx`.
- Conteudo deve conter `<OFX>`.
- Charset:
  - se header indicar `ENCODING:UTF-8`, usa UTF-8
  - senao usa ISO-8859-1

Regras de parsing:
- Transacoes lidas de blocos `<STMTTRN>`.
- Campo de data: `<DTPOSTED>` (usa apenas `yyyyMMdd`; ignora hora/fuso para persistencia como data local).
- Valor: `<TRNAMT>`.
- Historico: `<MEMO>` sanitizado e truncado para 120.

Regras de classificacao:
- Valor negativo -> cria `bill` com valor absoluto e `paid=true`.
- Valor positivo -> cria `income`.
- Categoria inicial para importados: `Outros`.

Deteccao de duplicidade:
- Chave para saida: `data|valor|descricao_normalizada`.
- Chave para entrada: `data|valor|origem_normalizada`.
- Duplicados sao ignorados e contabilizados.

Deteccao de transferencia interna no import:
1. Heuristica por memo no proprio import:
   - exige termos de transferencia (`PIX` ou `TRANSFER`)
   - e evidencia de titularidade por CPF ou nome
2. Deteccao cruzada pos-importacao (opcional, no frontend esta habilitada):
   - chama servico de matching de saida/entrada com mesmo valor e proximidade de data
   - pode auto-aplicar marcacao interna

Auditoria:
- Registra evento `statement/import` com arquivo e quantidade de transacoes.

Endpoint:
- `POST /api/v1/statements/import/ofx`

## 5.7 Exportacao de extrato OFX
Backend (consumo via Postman ou integracao futura no frontend):
- `GET /api/v1/statements/export/ofx`

Parametros:
- obrigatorios: `startDate`, `endDate`
- opcionais: `bankOrg`, `fid`, `bankId`, `branchId`, `accountId`, `currency`

Regras:
- `startDate` nao pode ser maior que `endDate`.
- Exporta:
  - saidas como `DEBIT` e valor negativo
  - entradas como `CREDIT` e valor positivo
- Ordena por data e `FITID`.
- Gera `LEDGERBAL` como soma algabrica das transacoes exportadas.
- Sanitiza campos textuais para formato OFX.
- Retorna arquivo `extrato_yyyymmdd_yyyymmdd.ofx`.

Auditoria:
- Registra evento `statement/export` com periodo e saldo do arquivo.

## 5.8 Transferencias internas
Backend:
- Manual: `POST /api/v1/transfers/internal/link`
- Deteccao: `POST /api/v1/transfers/internal/detect`

Regra de vinculacao manual:
- Marca conta e entrada com `internalTransfer=true`.

Regra de deteccao automatica:
- Candidatos precisam ter:
  - mesmo valor (tolerancia numerica pequena)
  - diferenca de data dentro da tolerancia configurada
- Score inicial e incrementado por evidencias:
  - CPF do titular no historico
  - tokens do nome do titular no historico
  - padrao textual de transferencia em ambos os lados
- Sem evidencia de titularidade (CPF/nome), par e descartado.
- Com score minimo, gera sugestao; pode auto-aplicar.
- Um registro nao pode ser pareado com varios no mesmo lote (controle de uso unico por id).

Impacto analitico:
- Movimentos internos deixam de contar nos indicadores financeiros.

## 5.9 Lixeira
Tela:
- `/lixeira`

Acoes:
- Restaurar item
- Excluir permanentemente item

Regras:
- Restauracao reidrata objeto serializado em JSON e grava de volta na tabela original por tipo.
- Expurgo automatico acontece quando `purge_at` vence (via rotina de limpeza de retencao).
- Expurgo manual remove imediatamente.

Filtros:
- Nome
- Tipo de entidade
- Periodo de exclusao

Ordenacao:
- Nome, tipo, data de exclusao, data de expurgo.

Visualizacao:
- Lista (cabecalho sticky)
- Grade

Endpoints:
- `GET /api/v1/governance/trash-items`
- `POST /api/v1/governance/trash-items/{trashId}/restore`
- `DELETE /api/v1/governance/trash-items/{trashId}`

## 5.10 Auditoria
Tela:
- `/auditoria`

Filtros:
- Data inicial/final
- Tipo de entidade
- Acao
- Nome
- Faixa de valor

Ordenacao:
- Quando, tipo, nome, valor, acao.

Visualizacao:
- Lista (cabecalho sticky)
- Grade

Eventos tipicos:
- `create`, `update`, `delete`, `restore`, `purge`, `toggle-status`
- `export`, `link-internal`, `auto-link-internal`

Endpoint:
- `GET /api/v1/governance/audit-events`

## 5.11 Configuracoes
Tela:
- `/configuracoes`

Blocos:
- Aparencia (tema claro/escuro)
- Padroes da aplicacao
- Retencao e auditoria

Padroes da aplicacao:
- Categoria padrao de saida/entrada
- Recorrencia padrao
- Dia padrao de vencimento/recebimento
- Modo padrao do dashboard
- Distancia padrao entre meses no dashboard
- Cadastro de categorias de saida e entrada (incluir, editar, excluir)

Regras de categorias:
- Minimo de 1 categoria em saidas e 1 em entradas.
- Nome nao pode ser vazio.
- Maximo 120 caracteres.
- Unicidade case-insensitive.
- Ao remover categoria padrao, sistema escolhe outra valida automaticamente.

Persistencia:
- Categories sao gravadas junto com preferencias (`billCategories`, `incomeCategories`).
- Backend normaliza listas, remove duplicadas e garante default valido.

Auditoria especifica de configuracoes:
- Alteracoes de retencao registram exatamente quais campos mudaram.
- Alteracoes de preferencias registram campo a campo, incluindo listas de categorias.

Retencao:
- `trashRetentionDays` e `auditRetentionDays` (1 a 3650 dias).
- Limpeza de retencao pode ser executada por endpoint.

Endpoints:
- `GET/PUT /api/v1/governance/app-preferences`
- `GET/PUT /api/v1/governance/retention-settings`
- `POST /api/v1/governance/retention-cleanup`

## 6. Modelo de dados Oracle (negocio)
Tabelas principais:
- `bills`
- `incomes`
- `planning_goals`
- `spending_goals`
- `trash_items`
- `audit_events`
- `retention_settings` (singleton id=1)
- `app_preferences` (singleton id=1)

Regras de integridade no banco (exemplos):
- Valores monetarios positivos em contas/entradas/metas.
- Flags booleanas modeladas como `NUMBER(1)` com check `(0,1)`.
- `default_dashboard_mode` restrito a `month|range`.
- Dias padrao restritos a 1..31.
- Offset de comparacao restrito a 1..12.

Migrations:
- `V1__init_schema.sql`: schema inicial
- `V2__add_internal_transfer_flag.sql`: flag de transferencia interna
- `V3__spending_goals_and_trash.sql`: metas de gasto + lixeira
- `V4__remove_demo_seed_data.sql`: remove seeds de demonstracao
- `V5__add_category_lists_to_app_preferences.sql`: listas de categorias em preferencias

## 7. Regras de validacao de API (resumo)
- BillRequest: descricao/categoria obrigatorias, valor >= 0.01, data obrigatoria.
- IncomeRequest: origem/categoria obrigatorias, valor >= 0.01, data obrigatoria.
- PlanningGoalRequest: titulo obrigatorio, alvo >= 0.01, atual >= 0, data obrigatoria.
- SpendingGoalRequest: titulo/categoria/schedule obrigatorios, limite >= 0.01, validacao semantica de periodo no service.
- RetentionSettingsRequest: 1..3650.
- AppPreferencesRequest:
  - categorias default obrigatorias
  - dias 1..31
  - modo `month|range`
  - offset 1..12
  - listas de categorias nao vazias
- Analytics: `endDate >= startDate`.

## 8. O que cada tela usa por baixo dos panos
- Todas as telas carregam dados via `FinanceFacade.loadAll()` no bootstrap.
- Dashboard, Saidas e Entradas respeitam filtro global de mes/intervalo via facade.
- Cada tela de listagem aplica filtro local + ordenacao local sobre os dados ja carregados.
- Persistencia sempre ocorre no backend; frontend apenas atualiza estado local apos resposta da API.
- Em operacoes de exclusao, dados somem da lista ativa e aparecem na lixeira (se exclusao logica aplicada).

## 9. Regras criticas para analise financeira correta
1. Transferencias internas devem ser corretamente marcadas para nao distorcer receitas/despesas.
2. Importacao OFX deve deduplicar para evitar inflar movimentacao.
3. Categorias precisam permanecer persistidas em `app_preferences`; sem isso formularios ficam inconsistentes.
4. Retencao da lixeira e auditoria precisa estar adequada para rastreabilidade e LGPD/governanca interna.
5. Filtros de periodo devem refletir datas reais de `dueDate` e `receivedAt` para analises coerentes.

## 10. Endpoints consolidados (referencia rapida)
- `GET/POST/PUT/DELETE /api/v1/bills`
- `GET/POST/PUT/DELETE /api/v1/incomes`
- `GET/POST/PUT/DELETE /api/v1/planning-goals`
- `GET/POST/PUT/DELETE /api/v1/spending-goals`
- `GET /api/v1/analytics/dashboard-summary`
- `POST /api/v1/statements/import/ofx`
- `GET /api/v1/statements/export/ofx`
- `POST /api/v1/transfers/internal/link`
- `POST /api/v1/transfers/internal/detect`
- `GET /api/v1/governance/audit-events`
- `GET /api/v1/governance/trash-items`
- `POST /api/v1/governance/trash-items/{trashId}/restore`
- `DELETE /api/v1/governance/trash-items/{trashId}`
- `GET/PUT /api/v1/governance/retention-settings`
- `GET/PUT /api/v1/governance/app-preferences`
- `POST /api/v1/governance/retention-cleanup`

---
Ultima revisao deste documento: 2026-03-01
