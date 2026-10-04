import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  root: "webview",
  base: "./",
  plugins: [react(), tailwindcss()],
  build: {
    outDir: "../dist-webview",
    emptyOutDir: true,
    target: "es2020",
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (/node_modules\/(react|react-dom|scheduler)\//.test(id)) return "react";
        },
      },
    },
  },
  server: { port: 1430, strictPort: true },
});
