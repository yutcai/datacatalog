import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies the API so the browser talks to one origin (no CORS in dev).
// In the built image, nginx does the same same-origin proxying.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/v1': 'http://localhost:8083',
      '/health': 'http://localhost:8083',
    },
  },
})
