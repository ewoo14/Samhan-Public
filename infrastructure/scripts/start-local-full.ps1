<#
.SYNOPSIS
    SamhanLogis 풀 수준 로컬 테스트 환경 일괄 기동 스크립트.

.DESCRIPTION
    14 backend MSA + 인프라 (postgres + redis + rabbitmq + elasticsearch + minio)
    를 의존 순서대로 기동하고 시드 데이터 row count 를 검증한다.

    동작 순서:
        1) docker-compose up -d (인프라 + 모니터링 stack)
        2) infrastructure/env-templates/.env.dev-seed 환경변수 일괄 로드
        3) 14 service 의존순 startup (Gradle bootRun, background job)
           eureka -> auth -> user -> product -> inventory -> slip -> accounting
                  -> partner -> partner-order -> arologis -> groupware
                  -> notification -> dashboard -> api-gateway
        4) 각 service 별 health check (~30초 대기, /actuator/health 200 폴링)
        5) 시드 데이터 row count psql 검증
        6) 결과 요약 출력 (16 user / 50 partner / 100 product / 100 slip / ...)

.PARAMETER SkipDocker
    docker-compose up -d 단계 생략 (인프라가 이미 떠 있는 경우).

.PARAMETER SkipServices
    backend service 기동 단계 생략 (인프라 + 시드 검증만).

.PARAMETER ServiceTimeoutSec
    각 service 기동 health check 최대 대기 (기본 60초).

.EXAMPLE
    .\infrastructure\scripts\start-local-full.ps1

.EXAMPLE
    .\infrastructure\scripts\start-local-full.ps1 -SkipDocker

.NOTES
    - Windows PowerShell 5.1 / PowerShell 7+ 호환
    - JDK 17 + Docker Desktop 사전 설치 필수
    - 영문 경로 권장 (C:\dev\SamhanLogis) — 한글 path 는 JDK 17 @argfile 인코딩 한계
    - UTF-8 로 저장 — 한글 주석 보존
#>

[CmdletBinding()]
param(
    [switch] $SkipDocker,
    [switch] $SkipServices,
    [int]    $ServiceTimeoutSec = 60
)

$ErrorActionPreference = 'Stop'

# -----------------------------------------------------------------------------
# 0. 사전 준비 — 경로 + 환경 검증
# -----------------------------------------------------------------------------
$ProjectRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
$InfraDir    = Join-Path $ProjectRoot 'infrastructure'
$ComposeFile = Join-Path $InfraDir   'docker-compose.yml'
$EnvSeedFile = Join-Path $InfraDir   'env-templates\.env.dev-seed'
$LogsDir     = Join-Path $ProjectRoot '.local-logs'

if (-not (Test-Path $LogsDir)) { New-Item -ItemType Directory -Path $LogsDir -Force | Out-Null }

Write-Host ''
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host ' SamhanLogis 풀 수준 로컬 테스트 환경 기동' -ForegroundColor Cyan
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host " ProjectRoot : $ProjectRoot"
Write-Host " ComposeFile : $ComposeFile"
Write-Host " EnvSeedFile : $EnvSeedFile"
Write-Host " LogsDir     : $LogsDir"
Write-Host ''

# JDK 17 + gradlew 존재 검증
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'java 명령을 찾을 수 없습니다. JDK 17 (Eclipse Temurin) 을 설치하고 PATH 에 등록하세요.'
}
$gradleW = Join-Path $ProjectRoot 'gradlew.bat'
if (-not (Test-Path $gradleW)) { throw "gradlew.bat 을 찾을 수 없습니다: $gradleW" }

# -----------------------------------------------------------------------------
# 1. 인프라 기동 (docker-compose up -d)
# -----------------------------------------------------------------------------
if (-not $SkipDocker) {
    Write-Host '[1/6] 인프라 stack 기동 (postgres + redis + rabbitmq + elasticsearch + minio + monitoring)' -ForegroundColor Yellow
    Push-Location $InfraDir
    try {
        docker compose -f docker-compose.yml up -d
        if ($LASTEXITCODE -ne 0) { throw 'docker compose up 실패' }
    } finally {
        Pop-Location
    }
    Write-Host '   인프라 healthy 대기 (~30초) ...' -ForegroundColor DarkGray
    Start-Sleep -Seconds 30
} else {
    Write-Host '[1/6] 인프라 기동 단계 생략 (-SkipDocker)' -ForegroundColor DarkGray
}

# -----------------------------------------------------------------------------
# 2. .env.dev-seed 환경변수 일괄 로드
# -----------------------------------------------------------------------------
Write-Host ''
Write-Host '[2/6] 시드 toggle 환경변수 로드' -ForegroundColor Yellow

if (-not (Test-Path $EnvSeedFile)) {
    throw ".env.dev-seed 파일을 찾을 수 없습니다: $EnvSeedFile"
}

$loaded = 0
Get-Content $EnvSeedFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line)        { return }
    if ($line.StartsWith('#')) { return }

    $parts = $line -split '=', 2
    if ($parts.Count -ne 2) { return }

    $name  = $parts[0].Trim()
    $value = $parts[1].Trim()
    if (-not $name) { return }

    Set-Item "env:$name" $value
    $loaded++
}
Write-Host "   $loaded 개 환경변수 로드 완료" -ForegroundColor Green

# DB 연결 자격증명 (env 파일에 없는 표준 default)
if (-not $env:DB_HOST)     { $env:DB_HOST     = 'localhost' }
if (-not $env:DB_PORT)     { $env:DB_PORT     = '5432' }
if (-not $env:DB_USER)     { $env:DB_USER     = 'samhan' }
if (-not $env:DB_PASSWORD) { $env:DB_PASSWORD = 'samhan_dev_pw' }

# Phase 8 chained-default 패턴 — service 별 *_DB_USER / *_DB_PASSWORD 자동 매핑
$dbAlias = @(
    'SAMHAN_PARTNER', 'SAMHAN_PRODUCT', 'SAMHAN_INVENTORY', 'SAMHAN_SLIP',
    'SAMHAN_ACCOUNTING', 'SAMHAN_PARTNER_ORDER', 'SAMHAN_AROLOGIS',
    'SAMHAN_GROUPWARE', 'SAMHAN_NOTIFICATION', 'SAMHAN_DASHBOARD',
    'SAMHAN_DC_CONFIG', 'SAMHAN_PARTNER_AUTH', 'SAMHAN_AUTH', 'SAMHAN_USER',
    'SAMHAN_LOGGING'
)
foreach ($p in $dbAlias) {
    if (-not (Get-Item "env:${p}_DB_USER"     -ErrorAction SilentlyContinue)) { Set-Item "env:${p}_DB_USER"     $env:DB_USER }
    if (-not (Get-Item "env:${p}_DB_PASSWORD" -ErrorAction SilentlyContinue)) { Set-Item "env:${p}_DB_PASSWORD" $env:DB_PASSWORD }
    if (-not (Get-Item "env:${p}_DB_HOST"     -ErrorAction SilentlyContinue)) { Set-Item "env:${p}_DB_HOST"     $env:DB_HOST }
    if (-not (Get-Item "env:${p}_DB_PORT"     -ErrorAction SilentlyContinue)) { Set-Item "env:${p}_DB_PORT"     $env:DB_PORT }
}

# -----------------------------------------------------------------------------
# 3. 14 service 의존순 startup (Gradle bootRun, background job)
# -----------------------------------------------------------------------------
# 의존 그래프:
#   tier 0: eureka-server                          (service discovery)
#   tier 1: auth-service                            (JWT issuer)
#   tier 2: user-service, product-service, partner-service
#   tier 3: inventory-service, accounting-service
#   tier 4: slip-service, partner-order-service, arologis-service
#   tier 5: groupware-service, notification-service
#   tier 6: dashboard-service                       (집계, 4 client 의존)
#   tier 7: api-gateway                             (모든 service registry 후 라우팅)
#
# 의존순 sequential 기동 — 각 service 가 health check 통과 후 다음 service 시작.

$services = @(
    @{ name = 'eureka-server';         port = 8761 },
    @{ name = 'auth-service';          port = 8081 },
    @{ name = 'user-service';          port = 8083 },
    @{ name = 'product-service';       port = 8084 },
    @{ name = 'partner-service';       port = 8095 },
    @{ name = 'inventory-service';     port = 8085 },
    @{ name = 'accounting-service';    port = 8087 },
    @{ name = 'slip-service';          port = 8086 },
    @{ name = 'partner-order-service'; port = 8088 },
    @{ name = 'arologis-service';      port = 8097 },
    @{ name = 'groupware-service';     port = 8092 },
    @{ name = 'notification-service';  port = 8093 },
    @{ name = 'dashboard-service';     port = 8094 },
    @{ name = 'api-gateway';           port = 8080 }
)

if (-not $SkipServices) {
    Write-Host ''
    Write-Host "[3/6] 14 service 의존순 기동 (timeout = ${ServiceTimeoutSec}s/service)" -ForegroundColor Yellow

    $jobs = @()
    foreach ($svc in $services) {
        $name = $svc.name
        $port = $svc.port
        $logFile = Join-Path $LogsDir "$name.log"

        Write-Host "   ▶ $name (port $port) 기동 ..." -ForegroundColor Cyan

        # bootRun 백그라운드 job
        $job = Start-Job -Name $name -ScriptBlock {
            param($root, $module, $log)
            Set-Location $root
            & "$root\gradlew.bat" ":services:${module}:bootRun" --console=plain *>&1 |
                Out-File -FilePath $log -Encoding utf8
        } -ArgumentList $ProjectRoot, $name, $logFile
        $jobs += [pscustomobject]@{ Name = $name; Port = $port; Job = $job; Log = $logFile }

        # health check polling
        $healthUrl = "http://localhost:${port}/actuator/health"
        $deadline  = (Get-Date).AddSeconds($ServiceTimeoutSec)
        $up = $false
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 3
            try {
                $r = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
                if ($r.StatusCode -eq 200) { $up = $true; break }
            } catch {
                # 미기동 — 계속 폴링
            }
        }
        if ($up) {
            Write-Host "     OK ($name healthy)" -ForegroundColor Green
        } else {
            Write-Host "     WARN — $name 가 ${ServiceTimeoutSec}s 안에 healthy 미달성. log: $logFile" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host '[3/6] backend service 기동 단계 생략 (-SkipServices)' -ForegroundColor DarkGray
}

# -----------------------------------------------------------------------------
# 4. service health 종합 요약
# -----------------------------------------------------------------------------
Write-Host ''
Write-Host '[4/6] service health 종합 요약' -ForegroundColor Yellow
$healthSummary = @()
foreach ($svc in $services) {
    $url = "http://localhost:$($svc.port)/actuator/health"
    $status = 'DOWN'
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $status = 'UP' }
    } catch { }
    $healthSummary += [pscustomobject]@{
        Service = $svc.name
        Port    = $svc.port
        Status  = $status
    }
}
$healthSummary | Format-Table -AutoSize

# -----------------------------------------------------------------------------
# 5. 시드 row count psql 검증
# -----------------------------------------------------------------------------
Write-Host ''
Write-Host '[5/6] 시드 데이터 row count 검증 (psql)' -ForegroundColor Yellow

$seedQueries = @(
    @{ db = 'user_db';            table = 'employees';                 expected = 16  },
    @{ db = 'partner_db';         table = 'partners';                  expected = 50  },
    @{ db = 'product_db';         table = 'products';                  expected = 100 },
    @{ db = 'inventory_db';       table = 'inventory_balances';        expected = 200 },
    @{ db = 'slip_db';            table = 'slips';                     expected = 100 },
    @{ db = 'partner_order_db';   table = 'partner_orders';            expected = 30  },
    @{ db = 'arologis_db';        table = 'dispatches';                expected = 20  },
    @{ db = 'accounting_db';      table = 'accounting_subjects';       expected = 65  },
    @{ db = 'groupware_db';       table = 'approval_lines';            expected = 5   },
    @{ db = 'notification_db';    table = 'notification_channels';     expected = 3   },
    @{ db = 'dashboard_db';       table = 'kpi_snapshots';             expected = 1   }
)

$rowSummary = @()
foreach ($q in $seedQueries) {
    $sql = "SELECT count(*)::text FROM $($q.table);"
    $count = '?'
    try {
        $raw = docker exec samhan-postgres psql -U samhan -d $q.db -tAc $sql 2>$null
        if ($LASTEXITCODE -eq 0 -and $raw) { $count = ($raw | Out-String).Trim() }
    } catch { }
    $verdict = if ($count -eq '?') { 'SKIP (table 미생성)' }
               elseif ([int]::TryParse($count, [ref]$null) -and ([int]$count -ge $q.expected)) { 'OK' }
               else { 'LOW' }
    $rowSummary += [pscustomobject]@{
        DB       = $q.db
        Table    = $q.table
        Expected = $q.expected
        Actual   = $count
        Verdict  = $verdict
    }
}
$rowSummary | Format-Table -AutoSize

# -----------------------------------------------------------------------------
# 6. 사용 가이드 출력
# -----------------------------------------------------------------------------
Write-Host ''
Write-Host '[6/6] 사용 가이드' -ForegroundColor Yellow
Write-Host ''
Write-Host ' 마스터 로그인 (CEO 김미선):' -ForegroundColor Cyan
Write-Host '   POST http://localhost:8080/api/auth/login'
Write-Host '   body: {"loginId":"kimmiseon","password":"samhan!2026"}'
Write-Host ''
Write-Host ' 모니터링:' -ForegroundColor Cyan
Write-Host '   Eureka       → http://localhost:8761'
Write-Host '   API Gateway  → http://localhost:8080'
Write-Host '   Prometheus   → http://localhost:9090'
Write-Host '   Grafana      → http://localhost:3100  (admin / samhan_dev_pw)'
Write-Host '   RabbitMQ UI  → http://localhost:15672 (samhan / samhan_dev_pw)'
Write-Host '   MinIO UI     → http://localhost:9001  (samhan / samhan_dev_pw)'
Write-Host ''
Write-Host ' service log:' -ForegroundColor Cyan
Write-Host "   $LogsDir\<service-name>.log"
Write-Host ''
Write-Host ' 종료:' -ForegroundColor Cyan
Write-Host '   .\infrastructure\scripts\stop-local-full.ps1'
Write-Host ''
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host ' 완료' -ForegroundColor Green
Write-Host '==============================================================' -ForegroundColor Cyan
