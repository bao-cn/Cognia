import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import electron from 'vite-plugin-electron'

export default defineConfig(({ command, mode }) => {
  const isDesktop = mode === 'desktop'

  return {
    base: isDesktop && command === 'build' ? './' : '/',
    plugins: [
      vue(),
      tailwindcss(),
      ...(isDesktop
        ? electron([
            {
              entry: 'electron/main.ts',
              vite: {
                build: {
                  outDir: 'dist-electron',
                  emptyOutDir: false,
                  lib: {
                    entry: 'electron/main.ts',
                    formats: ['cjs'],
                    fileName: () => 'main.cjs',
                  },
                  rollupOptions: {
                    external: ['electron'],
                    output: { format: 'cjs' },
                  },
                },
              },
            },
            {
              entry: 'electron/preload.ts',
              onstart(args) {
                args.reload()
              },
              vite: {
                build: {
                  outDir: 'dist-electron',
                  emptyOutDir: false,
                  lib: {
                    entry: 'electron/preload.ts',
                    formats: ['cjs'],
                    fileName: () => 'preload.cjs',
                  },
                  rollupOptions: {
                    external: ['electron'],
                    output: { format: 'cjs' },
                  },
                },
              },
            },
          ])
        : []),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: {
        // The browser calls /api/v1/... on the Vite origin and Vite forwards it
        // to Spring Boot, so no CORS handling is needed during development.
        '/api': {
          target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
