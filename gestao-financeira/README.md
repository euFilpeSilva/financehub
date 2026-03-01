# Gestao Financeira (Angular)

Aplicacao frontend completa para gestao financeira pessoal, integrada ao backend via HTTP.

## Tecnologias

- Angular 19 (standalone components + lazy routes)
- Signals e computed para estado derivado
- Reactive Forms
- TailwindCSS
- Lucide Angular (icones)

## Como executar

1. Instalar dependencias:

```bash
npm install
```

2. Rodar em desenvolvimento:

```bash
npm start
```

3. Abrir no navegador:

`http://localhost:4200`

## Build e testes

- Build de producao:

```bash
npm run build
```

- Testes:

```bash
npm test
```

## Visao geral da arquitetura

### Camadas

- `models`: contratos e tipos da aplicacao
- `services/finance.gateway.ts`: contrato de dados e implementacao mock
- `services/finance.facade.ts`: estado central, regras de negocio e orquestracao
- `features/*`: telas por dominio
- `shared/*`: componentes reaproveitaveis

### Preparado para backend real

A aplicacao usa `FinanceGateway` com `InjectionToken` e ja vem configurada para `HttpFinanceGateway`.
Configure a URL da API em:
- `src/environments/environment.ts` (dev)
- `src/environments/environment.prod.ts` (producao)

## Rotas da aplicacao

- `/dashboard`: analiticos, comparacoes e metas de gasto
- `/contas`: saidas (contas) do mes
- `/entradas`: entradas de caixa
- `/planejamento`: metas financeiras
- `/lixeira`: itens excluidos (restaurar/excluir permanente)
- `/auditoria`: trilha de auditoria com filtros
- `/configuracoes`: tema, retencao e padroes da aplicacao

## Funcionalidades implementadas

## 1) Dashboard (analiticos)

### O que faz

- Exibe indicadores de entradas, saidas, saldo e progresso de metas
- Mostra melhor mes para economizar e mes com maior gasto
- Lista desempenho mensal (`entradas`, `gastos`, `economia`)
- Compara periodos:
  - mes x mes
  - intervalo customizado x intervalo customizado

### Como usar

1. Acesse `/dashboard`
2. Escolha o modo de comparacao
3. Preencha os campos de periodo
4. Veja variacao de entradas, gastos e economia

## 2) Saidas (Contas)

### O que faz

- Cadastrar conta mensal
- Marcar conta como recorrente
- Gerar contas recorrentes para o mes selecionado
- Alterar status (`Pago`/`Pendente`)
- Editar conta
- Excluir conta (vai para lixeira)

### Como usar

1. Acesse `/contas`
2. Preencha formulario e salve
3. Use `Editar` para alterar
4. Use `Excluir` para mover para lixeira

## 3) Entradas

### O que faz

- Cadastrar entrada (salario, extra etc.)
- Marcar entrada recorrente
- Editar entrada
- Excluir entrada (vai para lixeira)

### Como usar

1. Acesse `/entradas`
2. Preencha e salve
3. Use `Editar` para alterar
4. Use `Excluir` para mover para lixeira

## 4) Planejamento (Metas financeiras)

### O que faz

- Criar metas (titulo, alvo, acumulado, prazo, notas)
- Atualizar progresso da meta
- Alterar status (em andamento/concluida)
- Editar meta
- Excluir meta (vai para lixeira)

### Como usar

1. Acesse `/planejamento`
2. Crie a meta no formulario
3. Atualize acumulado no card da meta
4. Edite/exclua pelos botoes da meta

## 5) Metas de gasto (Dashboard)

### O que faz

- Criar metas de limite de gasto por:
  - categoria especifica
  - todas as categorias
- Programacao:
  - mensal
  - intervalo personalizado
- Acompanhar consumo vs limite
- Editar meta de gasto
- Excluir meta de gasto (vai para lixeira)

### Como usar

1. No `/dashboard`, use o bloco `Metas de gasto`
2. Escolha categoria e programacao
3. Salve e acompanhe o status (`Dentro da meta`/`Acima da meta`)

## 6) Lixeira

### O que faz

- Centraliza itens excluidos de:
  - entradas
  - saidas
  - metas
  - metas de gasto
- Permite:
  - restaurar
  - excluir permanentemente
- Exibe tempo restante ate expirar

### Como usar

1. Acesse `/lixeira`
2. Use `Restaurar` para devolver item
3. Use `Excluir permanente` para remover de vez

## 7) Auditoria

### O que faz

- Registra ciclo de vida das entidades:
  - criacao
  - atualizacao
  - mudanca de status
  - exclusao
  - restauracao
  - exclusao permanente
- Inclui metadados de nome e valor quando aplicavel
- Filtros por:
  - periodo
  - nome
  - valor minimo/maximo

### Como usar

1. Acesse `/auditoria`
2. Preencha filtros desejados
3. Clique `Aplicar filtros` (ou ajuste campos para atualizacao automatica)
4. Consulte eventos filtrados

## 8) Configuracoes

### O que faz

- Tema:
  - claro
  - escuro
- Retencao:
  - dias de lixeira
  - dias de auditoria
- Padroes da aplicacao:
  - categoria/recorrencia/dia padrao de saidas
  - categoria/recorrencia/dia padrao de entradas
  - modo padrao de comparacao no dashboard
  - distancia padrao entre meses no comparador

### Como usar

1. Acesse `/configuracoes`
2. Altere blocos desejados
3. Salve
4. Novos registros e comportamento do dashboard passam a usar os padroes

## Filtros globais de periodo

No topo da aplicacao existe um filtro global por:

- mes
- data inicial
- data final

Esse filtro impacta listas e totais das telas que consomem o periodo atual.

## Tema escuro

- Toggle rapido no header
- Persistencia em `localStorage`
- Suporte a preferencia do sistema na inicializacao

## Regras de retencao

- Itens excluidos ficam visiveis na lixeira pelo periodo configurado
- Eventos de auditoria sao mantidos pelo periodo configurado
- Limpeza automatica ocorre durante carregamento de dados

## Estrutura de pastas (resumo)

```txt
src/app/
  core/
    theme.service.ts
  features/
    audit/
    bills/
    dashboard/
    incomes/
    planning/
    settings/
    trash/
  models/
    finance.models.ts
  services/
    finance.facade.ts
    finance.gateway.ts
  shared/
    global-filter-bar/
    summary-cards/
```

## Proximos passos sugeridos

- Substituir mock por gateway HTTP real
- Adicionar autenticacao e segregacao por usuario
- Implementar testes unitarios e de integracao das features principais
- Adicionar graficos visuais (linha/barra) para analiticos do dashboard

## Fluxos rapidos (onboarding e QA)

## Fluxo 1: Entrada completa (cadastro -> edicao -> exclusao -> restauracao -> auditoria)

1. Ir para `/entradas`
2. Cadastrar uma nova entrada e salvar
3. Clicar em `Editar`, alterar valor/categoria e salvar
4. Clicar em `Excluir` (item vai para lixeira)
5. Ir para `/lixeira` e clicar em `Restaurar`
6. Ir para `/auditoria` e validar eventos de criacao, atualizacao, exclusao e restauracao

## Fluxo 2: Saida com status e ciclo de vida

1. Ir para `/contas`
2. Criar uma conta
3. Alternar status entre `Pago` e `Pendente`
4. Editar descricao/valor e salvar
5. Excluir a conta
6. Em `/auditoria`, filtrar por nome da conta e validar trilha completa

## Fluxo 3: Meta financeira do planejamento

1. Ir para `/planejamento`
2. Criar uma meta com valor alvo e data
3. Atualizar acumulado da meta
4. Alternar status de andamento/conclusao
5. Editar meta e salvar
6. Excluir meta e restaurar via `/lixeira`

## Fluxo 4: Meta de gasto do dashboard

1. Ir para `/dashboard`
2. Criar meta de gasto (mensal ou intervalo customizado)
3. Editar parametros da meta
4. Ativar/inativar meta
5. Excluir meta de gasto
6. Conferir eventos em `/auditoria`

## Fluxo 5: Retencao e expiracao

1. Ir para `/configuracoes`
2. Reduzir `dias na lixeira` e `dias de auditoria`
3. Excluir itens para gerar dados
4. Reabrir a aplicacao/forcar recarga para acionar limpeza automatica
5. Verificar se itens/eventos antigos foram removidos conforme configuracao

## Fluxo 6: Preferencias padrao da aplicacao

1. Ir para `/configuracoes`
2. Definir categoria e recorrencia padrao de entradas e saidas
3. Definir dia padrao de vencimento/recebimento
4. Ir para `/contas` e `/entradas` e confirmar formulario preenchido com padroes
5. No `/dashboard`, validar modo de comparacao padrao e offset de meses
