import tailwindcss from "@tailwindcss/vite";
import { tanstackStart } from "@tanstack/react-start/plugin/vite";
import react from "@vitejs/plugin-react";
import mdx from "fumadocs-mdx/vite";
import urlJoin from "url-join";
import { defineConfig, type Plugin } from "vite";

const basePath = urlJoin("/", process.env.PUBLIC_DOCS_BASE_PATH ?? "/");

export default defineConfig({
  server: {
    port: 3000,
  },
  envPrefix: "PUBLIC_",
  base: basePath,
  plugins: [
    mdx(),
    tailwindcss(),
    tanstackStart({
      spa: {
        enabled: true,
        prerender: {
          outputPath: "index.html",
          enabled: true,
          crawlLinks: true,
        },
      },
      pages: [
        {
          path: "/docs",
        },
        {
          path: "/privacy",
        },
        {
          path: "/terms",
        },
        {
          path: "/api/search",
        },
        {
          path: "llms-full.txt",
        },
        {
          path: "llms.txt",
        },
      ],
    }),
    generate404Page(),
    react(),
  ],
  build: {
    chunkSizeWarningLimit: 1000,
  },
  resolve: {
    tsconfigPaths: true,
    alias: {
      tslib: "tslib/tslib.es6.js",
    },
  },
});

function generate404Page(): Plugin {
  const htmlContent = `\
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="refresh" content="0; url=/">
    <title>Redirecting...</title>
    <script>
        window.location.replace("${basePath}");
    </script>
</head>
</html>`;

  return {
    name: "generate-404.html",
    generateBundle() {
      this.emitFile({
        type: "asset",
        fileName: "404.html",
        source: htmlContent,
      });
    },
  };
}
