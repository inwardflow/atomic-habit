import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'mask-icon.svg'],
      manifest: {
        name: 'Atomic Habits',
        short_name: 'Atomic',
        description: 'A compassionate habit tracker for the anxious mind.',
        theme_color: '#4f46e5',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      }
    })
  ],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/agui': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Core React runtime
          'vendor-react': ['react', 'react-dom', 'react-router-dom'],
          // Charting library (heavy)
          'vendor-recharts': ['recharts'],
          // Animation library
          'vendor-framer': ['framer-motion'],
          // UI utilities
          'vendor-ui': ['lucide-react', 'react-hot-toast', 'canvas-confetti', 'clsx', 'tailwind-merge'],
          // Data & state
          'vendor-data': ['axios', 'zustand', 'date-fns'],
          // Markdown, AG-UI & JSON parsing
          'vendor-content': ['react-markdown', '@ag-ui/client', 'json5'],
        },
      },
    },
  },
})
