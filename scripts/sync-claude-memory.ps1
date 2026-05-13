# =============================================================================
# Claude Code 메모리 동기화 스크립트 (Windows PowerShell)
# =============================================================================
# 목적: repo 의 .claude/memory/ 를 사용자 홈 auto-memory 위치로 동기화
# 사용 시점:
#   1. git pull 직후 (다른 PC 에서 메모리 갱신 받았을 때)
#   2. .claude/memory/ 를 직접 편집한 직후 (사용자 홈으로 즉시 반영)
# 양방향 sync 가 아닌 단방향 (repo → 사용자 홈) — repo 가 source of truth
# =============================================================================

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Source = Join-Path $RepoRoot '.claude\memory'
$Dest = Join-Path $env:USERPROFILE '.claude\projects\c--dev-SamhanLogis\memory'

if (-not (Test-Path $Source)) {
    Write-Error "Source not found: $Source"
    exit 1
}

if (-not (Test-Path $Dest)) {
    New-Item -ItemType Directory -Path $Dest -Force | Out-Null
    Write-Host "Created: $Dest" -ForegroundColor Yellow
}

$files = Get-ChildItem -Path $Source -Filter '*.md' -File
$copied = 0
foreach ($file in $files) {
    $targetPath = Join-Path $Dest $file.Name
    Copy-Item -Path $file.FullName -Destination $targetPath -Force
    $copied++
}

Write-Host "Synced $copied memory files: $Source -> $Dest" -ForegroundColor Green
Write-Host "Claude Code 새 세션부터 갱신된 메모리 적용됨." -ForegroundColor Cyan
