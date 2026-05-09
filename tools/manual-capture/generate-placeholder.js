/**
 * Placeholder PNG 생성 — 사용자 검토 시 깨진 이미지 즉시 해소.
 *
 * 출력: tools/manual-capture/output/_placeholder-screenshot-pending.png
 *   - 1280×720 회색 박스 (#F0F2F5) + 중앙 한국어 안내 ("스크린샷 캡처 예정")
 *   - 운영자 매뉴얼 + 이전 슬라이스 docs 의 placeholder 잔재 일괄 교체용.
 *
 * 사용:
 *   node tools/manual-capture/generate-placeholder.js
 */
const sharp = require('sharp');
const path = require('node:path');

const OUT = path.resolve(__dirname, 'output', '_placeholder-screenshot-pending.png');

const svg = `
<svg width="1280" height="720" xmlns="http://www.w3.org/2000/svg">
  <rect width="100%" height="100%" fill="#F0F2F5"/>
  <rect x="40" y="40" width="1200" height="640" fill="#FFFFFF" stroke="#D0D7DE" stroke-width="2" rx="8"/>
  <text x="640" y="320" font-family="Malgun Gothic, sans-serif" font-size="48" font-weight="700" fill="#1F2328" text-anchor="middle">📸 스크린샷 캡처 예정</text>
  <text x="640" y="380" font-family="Malgun Gothic, sans-serif" font-size="20" fill="#656D76" text-anchor="middle">실 캡처는 후속 PR 또는 Stage 2/3 에서 일괄 적용 예정</text>
  <text x="640" y="420" font-family="Malgun Gothic, sans-serif" font-size="16" fill="#8C959F" text-anchor="middle">tools/manual-capture/ + docs/manual/screenshots/README.md 참조</text>
</svg>
`;

(async () => {
  await sharp(Buffer.from(svg)).png().toFile(OUT);
  console.log(`✅ placeholder 생성: ${OUT}`);
})();
