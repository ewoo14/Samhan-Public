<#
.SYNOPSIS
    SamhanLogis 풀 수준 로컬 테스트 환경 일괄 종료 스크립트.

.DESCRIPTION
    동작 순서:
        1) 14 service Spring Boot graceful shutdown
           - PowerShell Job (start-local-full.ps1 가 띄운 background) 우선 종료
           - actuator/shutdown POST 시도 (실패 시 process kill)
        2) docker-compose down (옵션 -RemoveVolumes 으로 volume 삭제)

.PARAMETER RemoveVolumes
    docker volume 까지 삭제 (postgres / redis / rabbitmq / es / minio 데이터 일체).
    주의: 시드 + 사용자 작업 데이터 일체 소실.

.PARAMETER KeepDocker
    docker compose down 단계 생략 (인프라 유지).

.EXAMPLE
    .\infrastructure\scripts\stop-local-full.ps1

.EXAMPLE
    # 인프라 + volume 까지 완전 초기화
    .\infrastructure\scripts\stop-local-full.ps1 -RemoveVolumes
#>

[CmdletBinding()]
param(
    [switch] $RemoveVolumes,
    [switch] $KeepDocker
)

$ErrorActionPreference = 'Continue'

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
$InfraDir    = Join-Path $ProjectRoot 'infrastructure'

Write-Host ''
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host ' SamhanLogis 풀 수준 로컬 테스트 환경 종료' -ForegroundColor Cyan
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host ''

# -----------------------------------------------------------------------------
# 1. 14 service graceful shutdown
# -----------------------------------------------------------------------------
$services = @(
    @{ name = 'api-gateway';           port = 8080 },
    @{ name = 'dashboard-service';     port = 8094 },
    @{ name = 'notification-service';  port = 8093 },
    @{ name = 'groupware-service';     port = 8092 },
    @{ name = 'arologis-service';      port = 8097 },
    @{ name = 'partner-order-service'; port = 8088 },
    @{ name = 'slip-service';          port = 8086 },
    @{ name = 'accounting-service';    port = 8087 },
    @{ name = 'inventory-service';     port = 8085 },
    @{ name = 'partner-service';       port = 8095 },
    @{ name = 'product-service';       port = 8084 },
    @{ name = 'user-service';          port = 8083 },
    @{ name = 'auth-service';          port = 8081 },
    @{ name = 'eureka-server';         port = 8761 }
)

Write-Host '[1/2] 14 service graceful shutdown' -ForegroundColor Yellow
foreach ($svc in $services) {
    $name = $svc.name
    $port = $svc.port
    $shutdownUrl = "http://localhost:${port}/actuator/shutdown"

    $stopped = $false

    # 1) actuator shutdown 시도 (Spring Boot 가 management.endpoint.shutdown.enabled=true 일 때)
    try {
        $r = Invoke-WebRequest -Uri $shutdownUrl -Method POST -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        if ($r.StatusCode -eq 200) {
            Write-Host "   ▶ $name (port $port) actuator shutdown OK" -ForegroundColor Green
            $stopped = $true
        }
    } catch {
        # actuator shutdown endpoint 비활성 — 다음 단계 fallback
    }

    # 2) PowerShell Job 종료 (start-local-full.ps1 가 띄운 background job)
    $job = Get-Job -Name $name -ErrorAction SilentlyContinue
    if ($job) {
        try {
            Stop-Job  -Job $job -ErrorAction SilentlyContinue
            Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
            if (-not $stopped) {
                Write-Host "   ▶ $name (port $port) job stopped" -ForegroundColor Green
                $stopped = $true
            }
        } catch { }
    }

    # 3) port 점유 process kill (최후 fallback)
    if (-not $stopped) {
        try {
            $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
            if ($conns) {
                foreach ($c in $conns) {
                    Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
                }
                Write-Host "   ▶ $name (port $port) process killed" -ForegroundColor Yellow
                $stopped = $true
            }
        } catch { }
    }

    if (-not $stopped) {
        Write-Host "   - $name (port $port) — 이미 정지됨 또는 미발견" -ForegroundColor DarkGray
    }
}

# -----------------------------------------------------------------------------
# 2. docker-compose down
# -----------------------------------------------------------------------------
if (-not $KeepDocker) {
    Write-Host ''
    Write-Host '[2/2] 인프라 stack 종료 (docker compose down)' -ForegroundColor Yellow
    Push-Location $InfraDir
    try {
        if ($RemoveVolumes) {
            Write-Host '   -RemoveVolumes 옵션 — volume 일체 삭제 (postgres/redis/rabbitmq/es/minio)' -ForegroundColor Red
            docker compose -f docker-compose.yml down -v
        } else {
            docker compose -f docker-compose.yml down
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Host '[2/2] docker compose down 단계 생략 (-KeepDocker)' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host ' 종료 완료' -ForegroundColor Green
Write-Host '==============================================================' -ForegroundColor Cyan
