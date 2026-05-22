import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

const apiProxyTarget = process.env.MIRUPLAY_WEB_CONTROL_PROXY || 'http://localhost:9978'

export default defineConfig({
  plugins: [vue()],
  base: '/',
  build: {
    outDir: resolve(__dirname, '../src/main/assets/web'),
    emptyOutDir: true,
    sourcemap: false
  },
  server: {
    proxy: {
      '/api': apiProxyTarget
    }
  }
})
