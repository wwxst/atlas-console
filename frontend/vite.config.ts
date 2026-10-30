import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': path.resolve(import.meta.dirname, './src'), '@ui': path.resolve(import.meta.dirname, './src/ui'), '@features': path.resolve(import.meta.dirname, './src/features') } },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/files': 'http://localhost:8080',
      '/uploads': 'http://localhost:8080',
    },
  },
})
