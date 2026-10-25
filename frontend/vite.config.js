import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/vendor/",
  plugins: [react()],
  build: { outDir: "../src/main/resources/static", emptyOutDir: true },
  server: { port: 5179, proxy: { "/vendor/api": "http://localhost:3017" } },
});
