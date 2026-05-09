import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

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
      '/api': 'http://10.137.32.118:9978'
    }
  }
})
