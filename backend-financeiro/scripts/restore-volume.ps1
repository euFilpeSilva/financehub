param(
  [Parameter(Mandatory = $true)]
  [string]$BackupFile,
  [string]$VolumeName = "backend-financeiro_oracle_data",
  [switch]$StartAfterRestore,
  [switch]$Force
)

$ErrorActionPreference = "Stop"

function Assert-CommandExists {
  param([string]$CommandName)
  if (-not (Get-Command $CommandName -ErrorAction SilentlyContinue)) {
    throw "Comando '$CommandName' nao encontrado no PATH."
  }
}

Assert-CommandExists -CommandName "docker"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot

$backupPath = if ([System.IO.Path]::IsPathRooted($BackupFile)) { $BackupFile } else { Join-Path $projectRoot $BackupFile }
if (-not (Test-Path $backupPath)) {
  throw "Arquivo de backup nao encontrado: $backupPath"
}
$resolvedBackupPath = (Resolve-Path $backupPath).Path
$backupDirectory = Split-Path -Parent $resolvedBackupPath
$backupFileName = Split-Path -Leaf $resolvedBackupPath

$checksumPath = "$resolvedBackupPath.sha256"
if (Test-Path $checksumPath) {
  $expectedHash = (Get-Content $checksumPath -TotalCount 1).Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)[0]
  $actualHash = (Get-FileHash -Path $resolvedBackupPath -Algorithm SHA256).Hash
  if ($expectedHash -ne $actualHash) {
    throw "Checksum invalido para o backup informado. Esperado: $expectedHash, atual: $actualHash."
  }
  Write-Host "Checksum validado com sucesso."
}
else {
  Write-Host "Aviso: checksum nao encontrado ($checksumPath). Restauracao seguira sem validacao criptografica."
}

if (-not $Force) {
  Write-Host ""
  Write-Host "ATENCAO: esta operacao substitui totalmente os dados atuais do volume '$VolumeName'."
  $confirmation = Read-Host "Digite RESTORE para continuar"
  if ($confirmation -ne "RESTORE") {
    throw "Operacao cancelada pelo usuario."
  }
}

Write-Host "Parando containers do projeto..."
docker compose down
if ($LASTEXITCODE -ne 0) {
  throw "Falha ao executar 'docker compose down'."
}

$volumeExists = docker volume ls --format "{{.Name}}" | Where-Object { $_ -eq $VolumeName }
if ($volumeExists) {
  Write-Host "Removendo volume atual '$VolumeName'..."
  docker volume rm $VolumeName | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw "Falha ao remover volume '$VolumeName'."
  }
}

Write-Host "Criando volume '$VolumeName'..."
docker volume create $VolumeName | Out-Null
if ($LASTEXITCODE -ne 0) {
  throw "Falha ao criar volume '$VolumeName'."
}

Write-Host "Restaurando backup '$backupFileName' para o volume..."
docker run --rm `
  -v "${VolumeName}:/volume" `
  -v "${backupDirectory}:/backup" `
  alpine sh -c "tar -xzf /backup/$backupFileName -C /volume"

if ($LASTEXITCODE -ne 0) {
  throw "Falha ao restaurar backup no volume."
}

if ($StartAfterRestore) {
  Write-Host "Subindo containers apos restauracao..."
  docker compose up -d
  if ($LASTEXITCODE -ne 0) {
    throw "Falha ao subir containers apos restauracao."
  }
}

Write-Host "Restauracao concluida com sucesso."

