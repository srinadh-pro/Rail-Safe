import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The API proxy keeps the browser on one origin in local dev and in `vite preview`,
// matching the /api/* rewrite used by Vercel in production.
const apiProxy = {
  '/api': {
    target: process.env.VITE_API_TARGET || 'http://localhost:8080',
    changeOrigin: true
  }
};

// Production output is a single deployable folder:
//   dist/          -> the original HTML/CSS/JS pages (copied from public/)
//   dist/live/     -> the React dashboard bundle
export default defineConfig({
  plugins: [react()],
  base: '/live/',
  publicDir: 'public',
  build: {
    outDir: 'dist/live',
    emptyOutDir: true,
    copyPublicDir: false,
    sourcemap: false
  },
  server: {
    port: 5173,
    proxy: apiProxy
  },
  preview: {
    port: 4173,
    proxy: apiProxy
  }
});
