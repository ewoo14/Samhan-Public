<#
.SYNOPSIS
    Prometheus 경보 rule 이 "git 에 있는 대로 런타임에 실제 로드됐는지" 를 검증한다.

.DESCRIPTION
    🔴 이 스크립트가 존재하는 이유 (#809 R8-DEVOPS-1, 2026-07-16):

    `infrastructure/prometheus/rules/*.yml` 은 git 에 멀쩡히 있고 promtool 문법 검사도 통과하는데,
    **런타임 Prometheus 에는 rule 이 하나도 로드돼 있지 않은** 상태가 13일간 방치됐다.

      - 컨테이너 생성  : 2026-07-02
      - rules 마운트 추가: 2026-07-15 (커밋 77ea69c77) ← 13일 후행
      - 결과            : GET /api/v1/rules = {"groups":[]}  = 경보 런타임 부재

    원인 2가지가 겹친다.
      1) `./prometheus/rules:/etc/prometheus/rules:ro` 는 **컨테이너 생성 시점** 마운트다.
         compose 파일에 나중에 추가해도 기존 컨테이너에는 반영되지 않는다.
         `docker restart` 로도 안 고쳐진다 — `up -d --force-recreate` 가 필요하다.
      2) 🔴 Prometheus 는 `rule_files` glob 이 **0개 매치해도 오류를 내지 않는다.**
         로그·헬스체크·기동 어디에도 신호가 없다.

    즉 **코드/설정/git 어디에도 결함이 없는데 런타임에만 결함이 존재**한다.
    정적 리뷰로는 원리상 잡을 수 없고, 실제로 #809 의 R1~R7 **7개 라운드 전부가 놓쳤다.**
    → 그래서 "실행되는 검사" 로 박제한다.

    검사 항목 (rule 파일이 추가될 때마다 자동 적용 — #809 전용이 아니다):
      1. git 의 rule 파일 목록  ↔  런타임 로드 목록  대조 (drift = 실패)
      2. 로드된 각 rule 의 health = "ok" 확인
      3. promtool 문법 검사 (선택 · Docker 필요)

.PARAMETER PrometheusUrl
    Prometheus base URL. 기본 http://localhost:9090

.PARAMETER RulesDir
    rule 파일 디렉토리. 기본 = 이 스크립트 기준 ../prometheus/rules

.PARAMETER SkipPromtool
    promtool 문법 검사를 건너뛴다 (Docker 미가용 환경).

.EXAMPLE
    .\verify-prometheus-rules.ps1
    # 라이브 QA 라운드마다 실행. exit 0 = 경보가 실제로 살아있음.

.NOTES
    실패 시 복구 (restart 아님 — force-recreate 여야 한다):
      docker compose -p infrastructure --project-directory <repo>/infrastructure `
        -f docker-compose.yml -f docker-compose.local-all.yml `
        up -d --force-recreate --no-deps prometheus
#>
[CmdletBinding()]
param(
    [string] $PrometheusUrl = 'http://localhost:9090',
    [string] $RulesDir,
    [switch] $SkipPromtool
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RulesDir)) {
    $RulesDir = Join-Path $PSScriptRoot '..\prometheus\rules'
}

$failures = New-Object System.Collections.Generic.List[string]

Write-Host ''
Write-Host '=== Prometheus rule 로드 검증 (#809 R8-DEVOPS-1 재발 방지) ===' -ForegroundColor Cyan
Write-Host ("  Prometheus : {0}" -f $PrometheusUrl)
Write-Host ("  Rules dir  : {0}" -f $RulesDir)
Write-Host ''

# ---------------------------------------------------------------------------
# 1. git 의 rule 파일 수집
# ---------------------------------------------------------------------------
if (-not (Test-Path $RulesDir)) {
    Write-Host "[FAIL] rule 디렉토리를 찾을 수 없습니다: $RulesDir" -ForegroundColor Red
    exit 1
}

$localFiles = @(Get-ChildItem -Path $RulesDir -Filter '*.yml' -File | Select-Object -ExpandProperty Name | Sort-Object)

if ($localFiles.Count -eq 0) {
    Write-Host '[SKIP] rule 파일이 없습니다 — 검증 대상 없음.' -ForegroundColor Yellow
    exit 0
}

Write-Host ("git rule 파일 {0}건: {1}" -f $localFiles.Count, ($localFiles -join ', '))

# ---------------------------------------------------------------------------
# 2. 런타임 로드 목록 조회
# ---------------------------------------------------------------------------
try {
    $resp = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/rules" -TimeoutSec 10
}
catch {
    Write-Host ''
    Write-Host "[FAIL] Prometheus 에 연결할 수 없습니다: $PrometheusUrl" -ForegroundColor Red
    Write-Host "       $($_.Exception.Message)"
    Write-Host '       스택이 떠 있는지 확인하세요 (docker compose ps prometheus).'
    exit 2
}

$groups = @()
if ($null -ne $resp.data -and $null -ne $resp.data.groups) {
    $groups = @($resp.data.groups)
}

# 🔴 이것이 R8-DEVOPS-1 의 정확한 증상이다.
if ($groups.Count -eq 0) {
    Write-Host ''
    Write-Host '[FAIL] /api/v1/rules 가 비어 있습니다 — 경보가 런타임에 하나도 없습니다.' -ForegroundColor Red
    Write-Host '       rule 파일은 git 에 있지만 컨테이너에 마운트되지 않은 상태입니다 (#809 R8-DEVOPS-1 과 동일 증상).'
    Write-Host ''
    Write-Host '       복구 (docker restart 로는 안 고쳐집니다):' -ForegroundColor Yellow
    Write-Host '         docker compose -p infrastructure --project-directory <repo>/infrastructure \'
    Write-Host '           -f docker-compose.yml -f docker-compose.local-all.yml \'
    Write-Host '           up -d --force-recreate --no-deps prometheus'
    exit 1
}

$loadedFiles = @(
    $groups |
        Select-Object -ExpandProperty file |
        ForEach-Object { Split-Path $_ -Leaf } |
        Sort-Object -Unique
)

Write-Host ("런타임 로드 파일 {0}건: {1}" -f $loadedFiles.Count, ($loadedFiles -join ', '))
Write-Host ''

# ---------------------------------------------------------------------------
# 3. drift 대조 — git 에는 있는데 런타임에 없는 파일
# ---------------------------------------------------------------------------
foreach ($f in $localFiles) {
    if ($loadedFiles -contains $f) {
        Write-Host ("  [OK]   {0} — 로드됨" -f $f) -ForegroundColor Green
    }
    else {
        Write-Host ("  [FAIL] {0} — git 에 있으나 런타임에 로드되지 않음 (마운트 누락 의심)" -f $f) -ForegroundColor Red
        $failures.Add("rule 파일 미로드: $f")
    }
}

# 런타임에는 있는데 git 에 없는 파일 = 유령 rule (경고)
foreach ($f in $loadedFiles) {
    if ($localFiles -notcontains $f) {
        Write-Host ("  [WARN] {0} — 런타임에 로드됐으나 git 에 없음 (stale 컨테이너 의심)" -f $f) -ForegroundColor Yellow
    }
}

# ---------------------------------------------------------------------------
# 4. rule health 확인 — 로드됐어도 평가에 실패하면 경보는 못 쓴다
# ---------------------------------------------------------------------------
Write-Host ''
Write-Host 'rule health:'
foreach ($g in $groups) {
    foreach ($r in @($g.rules)) {
        if ($r.health -eq 'ok') {
            Write-Host ("  [OK]   {0} / {1} — health=ok, state={2}" -f $g.name, $r.name, $r.state) -ForegroundColor Green
        }
        else {
            Write-Host ("  [FAIL] {0} / {1} — health={2} {3}" -f $g.name, $r.name, $r.health, $r.lastError) -ForegroundColor Red
            $failures.Add("rule health 이상: $($g.name)/$($r.name) health=$($r.health)")
        }
    }
}

# ---------------------------------------------------------------------------
# 5. promtool 문법 검사 (선택)
# ---------------------------------------------------------------------------
if (-not $SkipPromtool) {
    Write-Host ''
    Write-Host 'promtool 문법 검사:'
    $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
    if ($null -eq $dockerCmd) {
        Write-Host '  [SKIP] docker 미가용 — promtool 검사를 건너뜁니다.' -ForegroundColor Yellow
    }
    else {
        # promtool 은 디렉토리가 아니라 파일 경로들을 받는다 ('rules' is a directory 오류 방지).
        $abs = (Resolve-Path $RulesDir).Path
        $containerPaths = @($localFiles | ForEach-Object { "/rules/$_" })
        $out = & docker run --rm -v "${abs}:/rules:ro" --entrypoint promtool prom/prometheus:v2.55.1 check rules @containerPaths
        if ($LASTEXITCODE -eq 0) {
            Write-Host ('  [OK]   promtool check rules 통과 ({0}건)' -f $localFiles.Count) -ForegroundColor Green
        }
        else {
            Write-Host '  [FAIL] promtool check rules 실패:' -ForegroundColor Red
            $out | ForEach-Object { Write-Host "         $_" }
            $failures.Add('promtool check rules 실패')
        }
    }
}

# ---------------------------------------------------------------------------
# 결과
# ---------------------------------------------------------------------------
Write-Host ''
if ($failures.Count -gt 0) {
    Write-Host ("=== FAIL — {0}건 ===" -f $failures.Count) -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    Write-Host ''
    Write-Host '경보가 살아있지 않으면 fail-soft 유실을 탐지할 수단이 없습니다.' -ForegroundColor Red
    Write-Host 'runbook: docs/runbooks/slip-price-memory-upsert-failure.md (§0차 확인)'
    exit 1
}

Write-Host '=== PASS — git 의 모든 rule 이 런타임에 로드됐고 health 정상 ===' -ForegroundColor Green
exit 0
