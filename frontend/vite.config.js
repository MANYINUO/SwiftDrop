import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 后端地址，可用环境变量覆盖：BACKEND=http://192.168.1.10:8080 npm run dev
const BACKEND = process.env.BACKEND || 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // 用 @ 指向 src，避免一堆 ../../../
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: '127.0.0.1',
    // 用代理解决跨域：浏览器看到的是同源请求
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: true,
      },
      // WebSocket 也要代理，否则前端连不上推送
      '/ws': {
        target: BACKEND.replace(/^http/, 'ws'),
        ws: true,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500,
  },
})
