/**
 * SamhanLogis 운영자 매뉴얼 — 박스/화살표 자동 어노테이션 (Sharp + SVG overlay).
 *
 * 입력: 원본 PNG + annotations 배열
 *   annotation type:
 *     - 'box'    : { type, selector?, x, y, w, h, label }
 *                  → 붉은 사각형 (#FF0000, 2px) + 좌상단 한국어 label
 *     - 'arrow'  : { type, from: [x1, y1], to: [x2, y2], label }
 *                  → 노란 화살표 (#FFD700, 3px) + 끝점 label
 *
 * 출력: <원본>.annotated.png (동일 디렉토리)
 *
 * 호출 패턴:
 *   const { addAnnotations } = require('./annotate');
 *   await addAnnotations('output/00-login.png', [
 *     { type: 'box', x: 100, y: 200, w: 300, h: 40, label: '1. 로그인 ID 입력' },
 *     { type: 'arrow', from: [800, 300], to: [600, 200], label: '여기 클릭' },
 *   ]);
 *
 * Sharp 으로 SVG overlay 합성 — selector 좌표 해석은 capture-desktop.js / capture-mobile.js 가
 * Playwright `boundingBox()` 로 얻은 후 본 모듈에 전달.
 *
 * CLI 사용 (수동 어노테이션):
 *   node annotate.js <input.png> <annotations.json>
 */
const sharp = require('sharp');
const fs = require('node:fs');
const path = require('node:path');

const COLOR_BOX = '#FF0000';
const COLOR_ARROW = '#FFD700';
const COLOR_LABEL_BG = '#FFFFFF';
const COLOR_LABEL_TEXT = '#000000';
const COLOR_LABEL_BORDER = '#FF0000';
const FONT_SIZE = 14;
const FONT_FAMILY = "'Malgun Gothic', 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif";

/** XML 특수문자 escape — SVG 텍스트 안전 삽입. */
function escapeXml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/** 한 박스 어노테이션 → SVG fragment. */
function boxToSvg(ann) {
  const { x, y, w, h, label } = ann;
  const labelEsc = escapeXml(label || '');
  const labelW = Math.max(80, labelEsc.length * (FONT_SIZE * 0.65) + 12);
  const labelH = FONT_SIZE + 8;
  // label 은 박스 좌상단 위쪽 (음수 y) 에 배치, 화면 밖으로 나가면 박스 안쪽으로.
  const labelY = y - labelH - 2 < 0 ? y + 2 : y - labelH - 2;
  return `
    <rect x="${x}" y="${y}" width="${w}" height="${h}"
          fill="none" stroke="${COLOR_BOX}" stroke-width="2"/>
    <rect x="${x}" y="${labelY}" width="${labelW}" height="${labelH}"
          fill="${COLOR_LABEL_BG}" stroke="${COLOR_LABEL_BORDER}" stroke-width="1"/>
    <text x="${x + 6}" y="${labelY + FONT_SIZE + 1}"
          font-family="${FONT_FAMILY}" font-size="${FONT_SIZE}"
          fill="${COLOR_LABEL_TEXT}" font-weight="600">${labelEsc}</text>
  `;
}

/** 한 화살표 어노테이션 → SVG fragment (marker-end arrowhead). */
function arrowToSvg(ann, idx) {
  const [x1, y1] = ann.from;
  const [x2, y2] = ann.to;
  const labelEsc = escapeXml(ann.label || '');
  const markerId = `arrowhead-${idx}`;
  const labelW = Math.max(60, labelEsc.length * (FONT_SIZE * 0.65) + 12);
  const labelH = FONT_SIZE + 8;
  // label 은 화살표 끝점 (to) 옆에 배치.
  const labelX = x2 + 8;
  const labelY = y2 - labelH / 2;
  return `
    <defs>
      <marker id="${markerId}" markerWidth="10" markerHeight="10"
              refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
        <path d="M0,0 L0,6 L9,3 z" fill="${COLOR_ARROW}"/>
      </marker>
    </defs>
    <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"
          stroke="${COLOR_ARROW}" stroke-width="3"
          marker-end="url(#${markerId})"/>
    ${labelEsc ? `
      <rect x="${labelX}" y="${labelY}" width="${labelW}" height="${labelH}"
            fill="${COLOR_LABEL_BG}" stroke="${COLOR_ARROW}" stroke-width="1"/>
      <text x="${labelX + 6}" y="${labelY + FONT_SIZE + 1}"
            font-family="${FONT_FAMILY}" font-size="${FONT_SIZE}"
            fill="${COLOR_LABEL_TEXT}" font-weight="600">${labelEsc}</text>
    ` : ''}
  `;
}

/**
 * 원본 PNG 위에 SVG overlay 를 합성하여 .annotated.png 산출.
 *
 * @param {string} pngPath  원본 PNG 절대 경로
 * @param {Array}  annotations  resolved annotations (box 는 x/y/w/h 좌표 사전 해석 필수)
 * @returns {Promise<string>}  산출 경로
 */
async function addAnnotations(pngPath, annotations) {
  if (!fs.existsSync(pngPath)) {
    throw new Error(`[annotate] 원본 PNG 미존재: ${pngPath}`);
  }
  const meta = await sharp(pngPath).metadata();
  const width = meta.width;
  const height = meta.height;

  const fragments = annotations.map((ann, idx) => {
    if (ann.type === 'box') return boxToSvg(ann);
    if (ann.type === 'arrow') return arrowToSvg(ann, idx);
    console.warn(`[annotate] 알 수 없는 type: ${ann.type} — skip`);
    return '';
  });

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
    ${fragments.join('\n')}
  </svg>`;

  const outPath = pngPath.replace(/\.png$/i, '.annotated.png');
  await sharp(pngPath)
    .composite([{ input: Buffer.from(svg), top: 0, left: 0 }])
    .png()
    .toFile(outPath);

  return outPath;
}

module.exports = { addAnnotations, boxToSvg, arrowToSvg };

// CLI 진입 — 수동 호출 시 (디버그용).
if (require.main === module) {
  const [, , inputPng, annotationsJson] = process.argv;
  if (!inputPng || !annotationsJson) {
    console.error('Usage: node annotate.js <input.png> <annotations.json>');
    process.exit(1);
  }
  const ann = JSON.parse(fs.readFileSync(annotationsJson, 'utf8'));
  addAnnotations(path.resolve(inputPng), ann)
    .then((out) => console.log(`[annotate] 산출 → ${out}`))
    .catch((err) => {
      console.error(err);
      process.exit(1);
    });
}
