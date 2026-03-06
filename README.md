# FinanceHub - Monorepo

Ultima revisao: 2026-03-05

Aplicação de gestão financeira com:
- Backend Spring Boot (`backend-financeiro`)
- Frontend Angular (`gestao-financeira`)
- Oracle em container (`docker-compose.yml`)

## Publicação segura (GitHub)

1. Copie o arquivo de exemplo e preencha segredos locais:

```bash
cp .env.example .env
```

2. Defina senhas fortes no `.env` (nunca commitar esse arquivo).
3. Suba stack completa:

```bash
docker compose up --build
```

Serviços:
- Frontend: `http://localhost:4200`
- Backend: `http://localhost:8080`
- Oracle: `localhost:1521`

## Documentacao

- [DOCUMENTACAO.md](DOCUMENTACAO.md)
- [MANUAL_OPERACAO_SISTEMA.md](MANUAL_OPERACAO_SISTEMA.md)
- [REGRAS_NEGOCIO_APLICACAO.md](REGRAS_NEGOCIO_APLICACAO.md)
- [REQUISITOS_FUNCIONAIS_NAO_FUNCIONAIS.md](REQUISITOS_FUNCIONAIS_NAO_FUNCIONAIS.md)
- [STATUS_E_PROXIMOS_PASSOS.md](STATUS_E_PROXIMOS_PASSOS.md)

## Estrutura

- [backend-financeiro/README.md](backend-financeiro/README.md)
- [gestao-financeira/README.md](gestao-financeira/README.md)
- [docker-compose.yml](docker-compose.yml)

## Observações

- Arquivos `.env` são ignorados por padrão no `.gitignore` da raiz.
- Use apenas `.env.example` e `backend-financeiro/.env.example` como referência versionada.
