<#
.SYNOPSIS
    SamhanLogis MinIO 버킷 초기화 스크립트 (P0-3 거래처 첨부 + P1-8 슬립 모바일 사진).

.DESCRIPTION
    samhan-minio 컨테이너에 다음 버킷을 멱등 (idempotent) 으로 생성한다.

      - partner-attachments  : 거래처 첨부 (P0-3, PartnerAttachmentService) — 기존 패턴
      - slip-attachments     : 슬립 첨부 / 모바일 현장 사진 (P1-8, slip-service 도메인 확장)
                                매뉴얼 출처: docs/manual/04-모바일/04-사진-첨부.md §4-2 (V12 schema)

    각 버킷 정책:
      - bucket policy = private (인증 필요, 공개 anonymous read 금지)
      - 객체 다운로드는 presigned URL TTL 5분 (다운로드 link 단기) — 매뉴얼 §4-3
      - lifecycle (선택) 90일 후 STANDARD_IA tier 전환 (개발 단계 = 비활성, 운영 시 별도 활성)

    동작 순서:
        0) Pre-flight — docker / samhan-minio 컨테이너 가동 검증
        1) mc alias 등록 (samhan-minio admin)
        2) 각 버킷 멱등 생성 + private 정책 적용
        3) 결과 요약 출력

.PARAMETER MinioContainer
    MinIO 컨테이너 이름 (default samhan-minio).

.PARAMETER Endpoint
    MinIO API endpoint (default http://samhan-minio:9000 — same docker network).

.PARAMETER AccessKey
    MinIO root user (default samhan). DEV-ONLY.

.PARAMETER SecretKey
    MinIO root password (default samhan_dev_pw). DEV-ONLY.

.EXAMPLE
    .\infrastructure\scripts\setup-minio-buckets.ps1

.EXAMPLE
    .\infrastructure\scripts\setup-minio-buckets.ps1 -AccessKey $env:MINIO_ROOT_USER -SecretKey $env:MINIO_ROOT_PASSWORD

.NOTES
    - Windows PowerShell 5.1 / PowerShell 7+ 호환
    - UTF-8 (BOM) 로 저장 — 한글 주석 보존 (memory feedback_powershell_utf8_writes — Set-Content 미사용)
    - mc CLI 는 minio/mc:latest 컨테이너를 임시 spawn 하여 호스트 의존 0
    - 멱등성 — 이미 존재하는 버킷은 skip, 정책만 재적용 (CI 재실행 안전)
#>

[CmdletBinding()]
param(
    [string] $MinioContainer = 'samhan-minio',
    [string] $Endpoint       = 'http://samhan-minio:9000',
    [string] $AccessKey      = 'samhan',
    [string] $SecretKey      = 'samhan_dev_pw',
    [string] $Network        = 'samhan-net'
)

$ErrorActionPreference = 'Stop'

Write-Host ''
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host ' SamhanLogis MinIO 버킷 초기화 (P0-3 + P1-8)' -ForegroundColor Cyan
Write-Host '==============================================================' -ForegroundColor Cyan

# -----------------------------------------------------------------------------
# 0. Pre-flight — docker + minio container 검증
# -----------------------------------------------------------------------------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'docker 명령을 찾을 수 없습니다. Docker Desktop 을 시작하세요.'
}

# PS 5.1 native exe 가드 (memory feedback_powershell_utf8_writes — 동일 가드 패턴):
#   docker ps stderr 가 ErrorRecord 로 wrap → NativeCommandError → ErrorActionPreference='Stop'
#   상태에서 script abort. ErrorActionPreference 를 scope 로 풀고 stderr 만 무시 ($null).
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $running = docker ps --filter "name=$MinioContainer" --format '{{.Names}}' 2>$null
} finally {
    $ErrorActionPreference = $prevEAP
}
if (-not $running) {
    throw "MinIO 컨테이너 '$MinioContainer' 가 가동되어 있지 않습니다. 먼저 docker-compose up -d 실행 (infrastructure/docker-compose.yml)."
}

# -----------------------------------------------------------------------------
# 1. 버킷 정의 (P0-3 + P1-8)
# -----------------------------------------------------------------------------
# 각 버킷의 책임 / 매뉴얼 출처 / presigned TTL 명시.
$buckets = @(
    @{
        Name        = 'partner-attachments'
        Purpose     = '거래처 첨부 (P0-3, PartnerAttachmentService)'
        ManualRef   = 'docs/manual/01-영업/02-거래처-조회.md'
        PresignTtl  = 3600   # 1시간 (기존 패턴 보존 — PartnerAttachmentService.MAX_FILE_SIZE_BYTES = 10MB)
    },
    @{
        Name        = 'slip-attachments'
        Purpose     = '슬립 첨부 / 모바일 현장 사진 (P1-8, slip-service 도메인 확장)'
        ManualRef   = 'docs/manual/04-모바일/04-사진-첨부.md'
        PresignTtl  = 300    # 5분 (모바일 다운로드 link 단기 — 매뉴얼 §4-3)
    }
)

# -----------------------------------------------------------------------------
# 2. mc CLI (minio/mc:latest) — 임시 컨테이너로 alias + bucket 작업 일괄 수행
# -----------------------------------------------------------------------------
# mc 명령을 single-shot 컨테이너로 실행 — 호스트에 mc 설치 의존 0.
# samhan-net 네트워크에 join 하여 samhan-minio 와 직접 통신.
#
# PS 5.1 native exe 가드 — `& docker ... 2>&1` 은 stderr 를 stdout 으로 합치되 ErrorRecord 로
# wrap 하여 NativeCommandError 를 발생시킨다 (memory feedback_powershell_utf8_writes).
# 본 함수는 stdout 만 반환하고 stderr 는 PS 콘솔로 그대로 흘려보낸다 ($LASTEXITCODE 로 결과 판정).
function Invoke-Mc {
    param([string[]] $McArgs)

    $dockerArgs = @(
        'run', '--rm',
        '--network', $Network,
        '--entrypoint', 'mc',
        'minio/mc:latest'
    ) + $McArgs

    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & docker $dockerArgs
    } finally {
        $ErrorActionPreference = $prevEAP
    }
    return $LASTEXITCODE
}

Write-Host ''
Write-Host '[1/3] MinIO alias 등록 (samhan-minio)' -ForegroundColor Yellow
# mc alias set 은 ~/.mc 에 저장되나 --rm 컨테이너이므로 매 호출마다 재등록 필요.
# 본 호출은 검증 목적 — 실 작업은 각 mb / anonymous / policy 명령 시점에 alias 인자로 직접 전달.
# Invoke-Mc 는 stdout pipeline + 마지막 return $LASTEXITCODE 를 함께 emit 하므로
# 마지막 element 가 exit code, 그 앞이 docker stdout. 본 단계는 결과 표시 목적이라 단순히 합쳐 표시.
$aliasOut = Invoke-Mc @('alias', 'set', 'minio', $Endpoint, $AccessKey, $SecretKey)
Write-Host (($aliasOut | Out-String).Trim()) -ForegroundColor DarkGray

# -----------------------------------------------------------------------------
# 3. 각 버킷 멱등 생성 + private 정책
# -----------------------------------------------------------------------------
Write-Host ''
Write-Host '[2/3] 버킷 멱등 생성 + private 정책 적용' -ForegroundColor Yellow

$results = @()
foreach ($b in $buckets) {
    $name      = $b.Name
    $purpose   = $b.Purpose
    $manualRef = $b.ManualRef
    $ttl       = $b.PresignTtl

    Write-Host ""
    Write-Host "   ▶ $name" -ForegroundColor Cyan
    Write-Host "     - 용도        : $purpose"
    Write-Host "     - 매뉴얼 출처 : $manualRef"
    Write-Host "     - presigned TTL : ${ttl}s"

    # mc 작업 1 컨테이너에 묶어 alias + mb + anonymous 일괄 실행 (alias 휘발성 우회).
    # `mb --ignore-existing` 으로 멱등성 확보.
    # `anonymous set none` = private (모든 anonymous access 차단, presigned URL 만 다운로드 가능).
    #
    # CRLF 가드 — Windows here-string 은 CRLF 로 emit 되어 컨테이너 안 sh 가 `$'\r': command not found`
    # 에러를 stderr 로 throw → NativeCommandError noise. \r 를 제거하여 LF only 로 정규화.
    $script = (@"
mc alias set minio $Endpoint $AccessKey $SecretKey > /dev/null && \
mc mb --ignore-existing minio/$name && \
mc anonymous set none minio/$name
"@) -replace "`r", ""

    # PS 5.1 native exe 가드 — `2>&1` 는 docker stderr 를 ErrorRecord wrap 하여
    # NativeCommandError 발생 (memory feedback_powershell_utf8_writes).
    # ErrorActionPreference 를 scope 로 풀고 stderr 는 PS 콘솔로 그대로 흘려보낸다.
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & docker run --rm `
            --network $Network `
            --entrypoint sh `
            minio/mc:latest `
            -c $script
    } finally {
        $ErrorActionPreference = $prevEAP
    }

    $exit = $LASTEXITCODE
    if ($exit -eq 0) {
        Write-Host "     OK ($name 생성/검증 완료, private 정책 적용)" -ForegroundColor Green
        $results += [pscustomobject]@{ Bucket = $name; Status = 'OK'; TtlSec = $ttl }
    } else {
        Write-Host "     FAIL ($name)" -ForegroundColor Red
        Write-Host (($output | Out-String).Trim()) -ForegroundColor DarkGray
        $results += [pscustomobject]@{ Bucket = $name; Status = 'FAIL'; TtlSec = $ttl }
    }
}

# -----------------------------------------------------------------------------
# 4. 결과 요약 + 사용 가이드
# -----------------------------------------------------------------------------
Write-Host ''
Write-Host '[3/3] 결과 요약' -ForegroundColor Yellow
$results | Format-Table -AutoSize

$failed = $results | Where-Object { $_.Status -ne 'OK' }
if ($failed) {
    Write-Host ''
    Write-Host '==============================================================' -ForegroundColor Red
    Write-Host " 일부 버킷 생성 실패 — 위 로그 확인 + samhan-minio 컨테이너 재기동 후 재실행" -ForegroundColor Red
    Write-Host '==============================================================' -ForegroundColor Red
    exit 1
}

Write-Host ''
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host ' 완료 — 모든 버킷 정상' -ForegroundColor Green
Write-Host '==============================================================' -ForegroundColor Cyan
Write-Host ''
Write-Host ' 후속 가이드:' -ForegroundColor Cyan
Write-Host '   - partner-service : SAMHAN_PARTNER_MINIO_ENABLED=true 설정 후 bootRun'
Write-Host '   - slip-service    : SAMHAN_SLIP_MINIO_ENABLED=true 설정 후 bootRun (P1-8 활성 시)'
Write-Host '   - MinIO Console   : http://localhost:9001  (samhan / samhan_dev_pw)'
Write-Host ''
Write-Host ' lifecycle 활성 (90일 후 STANDARD_IA — 운영 시 별도 적용):' -ForegroundColor DarkGray
Write-Host '   docker run --rm --network samhan-net minio/mc:latest \' -ForegroundColor DarkGray
Write-Host '     ilm rule add minio/slip-attachments \' -ForegroundColor DarkGray
Write-Host '     --transition-days 90 --transition-tier STANDARD_IA' -ForegroundColor DarkGray
Write-Host ''
