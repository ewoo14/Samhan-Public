# order-legacy — Node.js 20 alpine, mem_limit 128m 권장.
# 도메인: order.samhan-air.com (port 5185).

FROM node:20-alpine AS base

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --omit=dev || npm install --omit=dev

COPY . .

EXPOSE 5185

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:5185/healthz || exit 1

ENV NODE_ENV=production
ENV PORT=5185

CMD ["node", "server.js"]
