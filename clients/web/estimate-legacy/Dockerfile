# estimate-legacy — Node.js 20 alpine, mem_limit 256m 권장 (카페24 RAM 1G 한계).
# 도메인: quote.samhan-air.com (port 5184).
#
# 빌드 시점에는 npm 의존성만 설치. lib/code.js / views/index.ejs / public/assets/*.html 은
# 사전 생성하여 git 에 commit 된 결과물을 그대로 복사.

FROM node:20-alpine AS base

WORKDIR /app

# 의존성 설치 (재현성 보장 — 본 PR 에 package-lock.json 동봉)
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev

# 본문 복사 — .dockerignore 가 node_modules / .env / lib/_legacy-code-raw.js 등 제외
COPY . .

EXPOSE 5184

# wget 으로 healthz GET — alpine 기본 설치되어 있음
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:5184/healthz || exit 1

ENV NODE_ENV=production
ENV PORT=5184

CMD ["node", "server.js"]
