import { defineConfig } from 'vite';
import angular from '@analogjs/vite-plugin-angular';

export default defineConfig({
  plugins: [angular()],
  server: {
    port: 4200,
    strictPort: true,
    // Increase limits for large file uploads
    maxRequestBodySize: '700mb',
    bodyParser: {
      limit: '700mb'
    }
  },
});