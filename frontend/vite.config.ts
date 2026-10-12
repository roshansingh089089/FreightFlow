import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    allowedHosts: [
  "hiv-backup-physical-tail.trycloudflare.com",
  "breakfast-publishers-schools-backing.trycloudflare.com",
],
    proxy: { '/api': 'http://localhost:8080' },
  },
});
