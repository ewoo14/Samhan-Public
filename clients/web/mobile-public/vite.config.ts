import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

// 모바일 공개 서명 웹앱 — 운영은 nginx 정적 서빙(sign.samhan-air.com), dev 는 proxy → api-gateway(8080).
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
  server: {
    port: 5185,
    host: true,
    proxy: { '/api': { target: process.env['VITE_DEV_PROXY_TARGET'] ?? 'http://localhost:8080', changeOrigin: true } },
  },
  preview: { port: 5186 },
  build: { outDir: 'dist', sourcemap: mode === 'development', target: 'es2020' },
}))
