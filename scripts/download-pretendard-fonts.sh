#!/usr/bin/env bash
# download-pretendard-fonts.sh
#
# Pretendard web font woff2 asset 을 jsdelivr / GitHub release 에서 받아
# 두 web client (order-app v4 + estimate-app v2) 의 public/fonts/ 디렉토리에 배치.
#
# self-host 목적:
#   - jsdelivr SPOF 회피 (CDN 장애 시 Pretendard 미적용 → FOUC + UI 깨짐)
#   - CSP font-src 'self' 만 허용 가능 (외부 도메인 의존 제거)
#   - 운영 환경의 외부 네트워크 출구 차단 정책 호환
#
# 저장소에는 woff2 파일을 commit 하지 않는다 (gitignore 됨).
# CI / 로컬 dev / production build 에서 본 script 를 사전 실행하여 asset 준비.
#
# 사용법:
#   bash scripts/download-pretendard-fonts.sh
#
# 멱등성: 이미 받은 파일이 있으면 skip.

set -euo pipefail

PRETENDARD_VERSION="${PRETENDARD_VERSION:-1.3.9}"
BASE_URL="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v${PRETENDARD_VERSION}/dist/web"

# 받을 weight + variable
STATIC_WEIGHTS=("Regular" "Bold")
VARIABLE_BASENAME="PretendardVariable.woff2"

# 두 client public/fonts 디렉토리
TARGETS=(
  "clients/web/order-app/public/fonts"
  "clients/web/estimate-app/public/fonts"
)

# 저장소 root 보장
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

download_one() {
  local url="$1"
  local out="$2"
  if [[ -f "${out}" ]]; then
    echo "  [skip] ${out} (이미 존재)"
    return 0
  fi
  mkdir -p "$(dirname "${out}")"
  echo "  [download] ${url}"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "${url}" -o "${out}"
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "${out}" "${url}"
  else
    echo "ERROR: curl 또는 wget 이 필요합니다." >&2
    exit 1
  fi
}

for target in "${TARGETS[@]}"; do
  echo "=== ${target} ==="
  for weight in "${STATIC_WEIGHTS[@]}"; do
    src="${BASE_URL}/static/woff2/Pretendard-${weight}.woff2"
    dst="${target}/Pretendard-${weight}.woff2"
    download_one "${src}" "${dst}"
  done
  # Variable
  src="${BASE_URL}/variable/woff2/${VARIABLE_BASENAME}"
  dst="${target}/${VARIABLE_BASENAME}"
  download_one "${src}" "${dst}"
done

echo ""
echo "Pretendard ${PRETENDARD_VERSION} self-host asset 준비 완료."
