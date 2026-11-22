import { defineConfig } from 'vite';

// Preview the exact folder published by Vercel: dist/ contains the original
// pages at the root and the React bundle under dist/live/.
export default defineConfig({
  // The built React HTML already uses absolute /live/assets/... URLs. Keeping
  // the preview base at '/' stops Vite from stripping the /live/ prefix.
  base: '/',
  build: {
    outDir: 'dist'
  },
  preview: {
    host: '127.0.0.1',
    port: 4173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
});
