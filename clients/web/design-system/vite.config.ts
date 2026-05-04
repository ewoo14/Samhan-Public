import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import dts from 'vite-plugin-dts'
import { resolve } from 'node:path'
import { copyFileSync, mkdirSync, existsSync } from 'node:fs'

/**
 * Copies `src/tokens/tokens.css` to `dist/tokens.css` so consumers can
 * `import '@samhan/design-system/tokens.css'` directly. Component CSS
 * Modules are still bundled as JS-imported styles inside `index.js`.
 */
function copyTokensCss(): Plugin {
  return {
    name: 'samhan-copy-tokens-css',
    apply: 'build',
    closeBundle() {
      const src = resolve(__dirname, 'src/tokens/tokens.css')
      const dest = resolve(__dirname, 'dist/tokens.css')
      if (!existsSync(resolve(__dirname, 'dist'))) {
        mkdirSync(resolve(__dirname, 'dist'), { recursive: true })
      }
      copyFileSync(src, dest)
    },
  }
}

export default defineConfig({
  plugins: [
    react(),
    dts({ insertTypesEntry: true, rollupTypes: true }),
    copyTokensCss(),
  ],
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      formats: ['es'],
      fileName: 'index',
    },
    rollupOptions: {
      external: ['react', 'react-dom', 'react/jsx-runtime'],
    },
    cssCodeSplit: false,
  },
})
