import react from '@vitejs/plugin-react';  
import { defineConfig } from 'vite';  

export default defineConfig({  
  plugins: [react()],  
  server: {  
    port: 5173,  
    proxy: {  
      '/api': { target: 'http://localhost:9000', changeOrigin: true },  
      '/ws': { target: 'ws://localhost:9000', ws: true }  
    }  
  },  
  build: {  
    outDir: '../server/src/main/resources/static',  
    emptyOutDir: true,  
    rollupOptions: { input: 'index.html' }  
  }  
});