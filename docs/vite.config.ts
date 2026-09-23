import tailwindcss from "@tailwindcss/vite";
import { tanstackStart } from "@tanstack/react-start/plugin/vite";
import react from "@vitejs/plugin-react";
import mdx from "fumadocs-mdx/vite";
import { defineConfig, type Plugin } from "vite";
import { discoverQrInstallationVersions } from "#/components/qr/version.discovery";
import { CLIENT_ENV_PREFIX, env } from "#/lib/env";

type QrInstallationVersionsPromise = ReturnType<
  typeof discoverQrInstallationVersions
>;

const qrInstallationVersionsCache = globalThis as typeof globalThis & {
  __webviewKioskQrInstallationVersionsPromise?: QrInstallationVersionsPromise;
};

function getQrInstallationVersions(): QrInstallationVersionsPromise {
  if (
    !qrInstallationVersionsCache.__webviewKioskQrInstallationVersionsPromise
  ) {
    qrInstallationVersionsCache.__webviewKioskQrInstallationVersionsPromise =
      discoverQrInstallationVersions({
        githubToken: process.env.GITHUB_TOKEN,
      });
  }
  return qrInstallationVersionsCache.__webviewKioskQrInstallationVersionsPromise;
}

export default defineConfig(async ({ command }) => {
  const qrInstallationVersions =
    command === "build" ? await getQrInstallationVersions() : {};

  return {
    server: {
      port: 3000,
    },
    envPrefix: CLIENT_ENV_PREFIX,
    base: env.PUBLIC_DOCS_BASE_PATH,
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
    define: {
      __QR_INSTALLATION_DISCOVERED_VERSIONS__: JSON.stringify(
        qrInstallationVersions,
      ),
    },
  };
});

function generate404Page(): Plugin {
  const htmlContent = `\
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="refresh" content="0; url=${env.PUBLIC_DOCS_BASE_PATH}">
    <title>Redirecting...</title>
    <script>
        window.location.replace("${env.PUBLIC_DOCS_BASE_PATH}");
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
