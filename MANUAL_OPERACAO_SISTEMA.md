# Manual de Operacao do Sistema

## Escopo

Este manual cobre a operação local do sistema Finance Hub como um todo, incluindo backend (`backend-financeiro`), frontend (`gestao-financeira`) e serviços de infraestrutura no `docker-compose.yml` (Oracle, API e UI).

Este documento descreve, de forma operacional, como executar, parar, preservar dados e recuperar o ambiente da aplicação.

## 1) Objetivo

Garantir que:
- o backend Spring e o Oracle subam de forma previsivel;
- os dados persistam entre desligamentos/reinicios;
- exista processo seguro de backup e restore;
- operacoes manuais reduzam risco de perda de dados.

## 2) Arquitetura operacional atual

Servicos no `docker-compose.yml` (raiz do projeto):
- `oracle-db`: banco Oracle Free 23c.
- `backend-financeiro`: API Spring Boot.
- `frontend-financeiro`: aplicacao Angular servida por Nginx.

Persistencia:
- volume Docker `oracle_data` montado em `/opt/oracle/oradata`.
- migrations Flyway versionadas (`V1`, `V2`, ...).
- `ddl-auto=validate` (nao recria schema em runtime).

Consequencia pratica:
- reiniciar aplicacao/container/maquina **nao restaura** dados excluidos manualmente.
- dados so "voltam" se voce restaurar backup, recriar volume ou aplicar migration que reinsira registros.

## 3) Pre-requisitos

- Docker Desktop (Linux containers).
- PowerShell (Windows).
- Porta `1521` livre (Oracle), `8080` livre (API) e `4200` livre (frontend).

## 4) Setup inicial

No diretorio raiz do projeto:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
```

Validar:

```powershell
docker compose ps
docker compose logs -f oracle-db
docker compose logs -f backend-financeiro
```

## 5) Operacao diaria

### 5.1 Subir ambiente

```powershell
docker compose up -d
```

### 5.2 Parar ambiente sem perder dados

```powershell
docker compose stop
```

ou

```powershell
docker compose down
```

`down` remove containers e rede, mas preserva volume.

### 5.3 Reiniciar ambiente

```powershell
docker compose down
docker compose up -d
```

## 6) Comandos proibidos em rotina (risco de perda)

Nao execute sem intencao explicita de reset:

```powershell
docker compose down -v
docker volume rm <nome-do-volume-oracle_data-do-projeto>
docker system prune --volumes
```

Esses comandos removem dados persistidos.

## 7) Integridade de dados apos desligar o computador

Cenario: voce removeu registros e desligou a maquina.

Ao ligar novamente:

```powershell
docker compose up -d
```

Comportamento esperado:
- banco volta com estado persistido no volume;
- registros removidos continuam removidos.

Checklist de verificacao:
1. `docker compose ps` mostra os dois servicos em execucao.
2. `docker volume ls` contem o volume `oracle_data` do projeto.
3. consulta no banco confirma estado esperado.

## 8) Backup e restore (obrigatorio para seguranca operacional)

Scripts disponiveis:
- `scripts/backup-volume.ps1`
- `scripts/restore-volume.ps1`

### 8.1 Backup do volume Oracle

Executar:

```powershell
.\scripts\backup-volume.ps1
```

Com parametros:

```powershell
.\scripts\backup-volume.ps1 -BackupDir .\backups -RetainDays 15
```

O que o script faz:
1. valida `docker` no PATH;
2. valida existencia do volume Oracle;
3. gera `oracle_data_YYYYMMDD_HHMMSS.tar.gz`;
4. gera checksum `SHA256` (`.sha256`);
5. opcionalmente remove backups antigos por retenção.

### 8.2 Restore de backup

Executar:

```powershell
.\scripts\restore-volume.ps1 -BackupFile .\backups\oracle_data_20260228_013000.tar.gz -StartAfterRestore
```

Comportamento:
1. valida arquivo e checksum (se existir);
2. pede confirmacao (`RESTORE`) para evitar erro humano;
3. derruba stack (`docker compose down`);
4. remove e recria volume alvo;
5. restaura tar no volume;
6. opcionalmente sobe stack novamente.

Observacao critica:
- restore substitui integralmente os dados atuais pelo backup escolhido.

## 9) Atualizacao de versao / deploy local

Se houver alteracoes no backend:

```powershell
docker compose build backend-financeiro
docker compose up -d
```

Ou rebuild completo:

```powershell
docker compose up --build -d
```

## 10) Validacoes de saude

- API:
```powershell
curl http://localhost:8080/actuator/health
```

- Frontend:
```powershell
curl http://localhost:4200
```

- Export OFX:
```powershell
curl "http://localhost:8080/api/v1/statements/export/ofx?startDate=2026-02-01&endDate=2026-02-28"
```

## 11) Acesso ao banco via DBeaver

Configuracao:
- Driver: Oracle
- Host: `localhost`
- Port: `1521`
- Service Name: `FREEPDB1`
- User: `financehub`
- Password: `financehub123` (ou valor do `.env`)

## 12) Troubleshooting

### Oracle nao sobe
1. Ver logs:
```powershell
docker compose logs -f oracle-db
```
2. Verifique memoria do Docker Desktop (Oracle precisa memoria adequada).
3. Confirme portas livres (`1521`).

### Backend nao conecta no banco
1. Ver logs backend:
```powershell
docker compose logs -f backend-financeiro
```
2. Confirmar variaveis `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
3. Confirmar Oracle saudavel no `docker compose ps`.

### Dados "sumiram"
1. Verificar se houve `down -v` ou remocao de volume.
2. Verificar volume existente:
```powershell
docker volume ls
```
3. Restaurar backup com `restore-volume.ps1`.

## 13) Politica recomendada de backup

Minimo recomendado:
- backup diario antes de alteracoes manuais relevantes;
- retenção de 15 a 30 dias;
- copia externa (pasta sincronizada/drive seguro).

## 14) Checklist operacional rapido

Inicio do dia:
1. `docker compose up -d`
2. validar `docker compose ps`
3. validar `actuator/health`

Fim do dia:
1. executar `backup-volume.ps1`
2. confirmar arquivo e checksum em `backups`
3. `docker compose stop` (opcional)
