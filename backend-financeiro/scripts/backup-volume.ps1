param(
  [string]$VolumeName = "backend-financeiro_oracle_data",
  [string]$BackupDir = ".\backups",
  [int]$RetainDays = 0
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

$volumeExists = docker volume ls --format "{{.Name}}" | Where-Object { $_ -eq $VolumeName }
if (-not $volumeExists) {
  throw "Volume '$VolumeName' nao encontrado. Suba a stack ao menos uma vez com 'docker compose up -d'."
}

$backupPath = if ([System.IO.Path]::IsPathRooted($BackupDir)) { $BackupDir } else { Join-Path $projectRoot $BackupDir }
if (-not (Test-Path $backupPath)) {
  New-Item -ItemType Directory -Path $backupPath | Out-Null
}

$resolvedBackupPath = (Resolve-Path $backupPath).Path
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = "oracle_data_$timestamp.tar.gz"

Write-Host "Gerando backup do volume '$VolumeName'..."
docker run --rm `
  -v "${VolumeName}:/volume" `
  -v "${resolvedBackupPath}:/backup" `
  alpine sh -c "tar -czf /backup/$backupFile -C /volume ."

if ($LASTEXITCODE -ne 0) {
  throw "Falha ao gerar backup do volume."
}

$backupFullPath = Join-Path $resolvedBackupPath $backupFile
$hash = Get-FileHash -Path $backupFullPath -Algorithm SHA256
$hashFile = "$backupFullPath.sha256"
"$($hash.Hash)  $backupFile" | Out-File -FilePath $hashFile -Encoding ascii

Write-Host "Backup criado com sucesso:"
Write-Host " - Arquivo: $backupFullPath"
Write-Host " - Checksum: $hashFile"

if ($RetainDays -gt 0) {
  $threshold = (Get-Date).AddDays(-$RetainDays)
  Get-ChildItem -Path $resolvedBackupPath -Filter "oracle_data_*.tar.gz" `
  | Where-Object { $_.LastWriteTime -lt $threshold } `
  | ForEach-Object {
      Write-Host "Removendo backup antigo: $($_.FullName)"
      Remove-Item -Path $_.FullName -Force
      $checksumCandidate = "$($_.FullName).sha256"
      if (Test-Path $checksumCandidate) {
        Remove-Item -Path $checksumCandidate -Force
      }
    }
}

Write-Host "Concluido."

