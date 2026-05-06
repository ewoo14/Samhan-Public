#!/usr/bin/env bash
#
# 카페24 SSH 연결 검증 dry-run script
# ===================================
#
# 본 script 는 카페24 SSH 접속 가능 여부 + 가용 자원/도구 확인만 진행한다.
# 실 배포는 수행하지 않는다 (rsync / pm2 reload / pm2 start 모두 미포함).
#
# 사용 시점:
#   1. 카페24 콘솔 (https://hosting.cafe24.com/) 에서 public key 등록 직후
#   2. 호스트/계정/포트가 변경된 후
#   3. CI workflow 활성화 (`deploy-cafe24-ssh.yml.template` 의 `.template` 제거)
#      이전, 사전 검증 단계
#
# 실행 예시 (로컬 개발자 머신):
#   chmod +x infrastructure/cafe24/test-ssh-connection.sh
#   CAFE24_HOST=203.245.41.148 \
#   CAFE24_USER=root \
#   CAFE24_SSH_KEY=~/.ssh/id_ed25519_cafe24 \
#     ./infrastructure/cafe24/test-ssh-connection.sh
#
# 본 script 는 CI 자동 실행 대상이 아니다 (개발자가 수동 실행).
# 결과는 stdout 으로만 출력하며, secrets / private key 내용은 절대 출력하지 않는다.

set -euo pipefail

CAFE24_HOST="${CAFE24_HOST:-}"
CAFE24_USER="${CAFE24_USER:-}"
CAFE24_PORT="${CAFE24_PORT:-22}"
CAFE24_SSH_KEY="${CAFE24_SSH_KEY:-$HOME/.ssh/id_ed25519_cafe24}"
CAFE24_SSH_TIMEOUT="${CAFE24_SSH_TIMEOUT:-10}"

print_header() {
  echo ""
  echo "============================================================"
  echo "  $1"
  echo "============================================================"
}

require_var() {
  local name="$1"
  local value="$2"
  if [[ -z "${value}" ]]; then
    echo "[FAIL] 환경변수 미설정: ${name}" >&2
    exit 2
  fi
}

print_header "0. 사전 환경 검증"
require_var "CAFE24_HOST" "${CAFE24_HOST}"
require_var "CAFE24_USER" "${CAFE24_USER}"

if [[ ! -f "${CAFE24_SSH_KEY}" ]]; then
  echo "[FAIL] SSH key 파일 미존재: ${CAFE24_SSH_KEY}" >&2
  echo "       카페24 콘솔에 등록한 public key 의 private key 경로를 CAFE24_SSH_KEY 로 지정." >&2
  exit 3
fi

# private key 권한 검증 (0600 권장 — 0644 이상이면 ssh 가 거부)
key_perm=$(stat -c '%a' "${CAFE24_SSH_KEY}" 2>/dev/null || stat -f '%A' "${CAFE24_SSH_KEY}" 2>/dev/null || echo "unknown")
echo "[INFO] SSH host=${CAFE24_HOST} user=${CAFE24_USER} port=${CAFE24_PORT}"
echo "[INFO] SSH key path=${CAFE24_SSH_KEY} perm=${key_perm}"

if [[ "${key_perm}" != "600" && "${key_perm}" != "400" ]]; then
  echo "[WARN] SSH private key 권한이 0600/0400 이 아니다 (현재 ${key_perm}). 'chmod 600 ${CAFE24_SSH_KEY}' 권장." >&2
fi

ssh_opts=(
  -i "${CAFE24_SSH_KEY}"
  -p "${CAFE24_PORT}"
  -o "ConnectTimeout=${CAFE24_SSH_TIMEOUT}"
  -o "BatchMode=yes"
  -o "StrictHostKeyChecking=accept-new"
  -o "UserKnownHostsFile=${HOME}/.ssh/known_hosts_cafe24"
  -o "PasswordAuthentication=no"
  -o "PubkeyAuthentication=yes"
)

print_header "1. SSH 인증 검증 (whoami / hostname / uptime)"
ssh "${ssh_opts[@]}" "${CAFE24_USER}@${CAFE24_HOST}" \
  'echo "whoami=$(whoami)"; echo "hostname=$(hostname)"; echo "uptime=$(uptime -p 2>/dev/null || uptime)"'

print_header "2. 자원 점검 (nproc / free -m / df -h)"
ssh "${ssh_opts[@]}" "${CAFE24_USER}@${CAFE24_HOST}" '
  echo "--- CPU ---"
  echo "nproc=$(nproc 2>/dev/null || echo unknown)"
  echo "--- 메모리 (MB) ---"
  free -m 2>/dev/null || vm_stat 2>/dev/null || echo "free/vm_stat 불가"
  echo "--- 디스크 (GB) ---"
  df -h 2>/dev/null | head -10
'

print_header "3. 도구 점검 (docker / pm2 / nginx / node / npm)"
ssh "${ssh_opts[@]}" "${CAFE24_USER}@${CAFE24_HOST}" '
  for cmd in docker pm2 nginx node npm rsync git; do
    path=$(command -v "$cmd" 2>/dev/null || echo "MISSING")
    if [ "$path" = "MISSING" ]; then
      echo "[X] $cmd : 미설치"
    else
      ver=$("$cmd" --version 2>/dev/null | head -1)
      echo "[O] $cmd : $path ($ver)"
    fi
  done
'

print_header "4. pm2 process 목록 (기존 운영 service 확인)"
ssh "${ssh_opts[@]}" "${CAFE24_USER}@${CAFE24_HOST}" '
  if command -v pm2 >/dev/null 2>&1; then
    pm2 list 2>/dev/null || echo "pm2 list 출력 없음"
  else
    echo "pm2 미설치 — 신규 service 추가 시 npm i -g pm2 선행 필요"
  fi
'

print_header "5. /home 디스크 사용량 (estimate-app 배포 가용 확인)"
ssh "${ssh_opts[@]}" "${CAFE24_USER}@${CAFE24_HOST}" '
  if [ -d /home ]; then
    du -sh /home/* 2>/dev/null | head -10
  else
    echo "/home 디렉토리 미존재"
  fi
'

print_header "RESULT: SSH 연결 + 자원 + 도구 검증 완료 (실 배포 X)"
echo "다음 단계:"
echo "  - 본 script 결과를 docs/dev-reports/phase7-step-1.md 의 cafe24 ssh 검증 섹션에 첨부"
echo "  - D6 (배포 대상 앱) / D7 (디렉토리) / D8 (pm2 명명) 답변 후"
echo "    .github/workflows/deploy-cafe24-ssh.yml.template 의 .template suffix 제거 검토"
