import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    allowedHosts: [
  "tied-personnel-devon-programmer.trycloudflare.com",
  "contemporary-spokesman-stuart-carter.trycloudflare.com",
],
    proxy: { '/api': 'http://localhost:8080' },
  },
});
