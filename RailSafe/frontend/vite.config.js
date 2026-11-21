import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Built assets are written into the Spring Boot static folder so the existing
// application keeps serving everything from http://localhost:8080/live/.
export default defineConfig({
  plugins: [react()],
  base: '/live/',
  build: {
    outDir: '../src/main/resources/static/live',
    emptyOutDir: true,
    sourcemap: false
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
});
