import tailwindcss from "@tailwindcss/vite";
import { tanstackStart } from "@tanstack/react-start/plugin/vite";
import react from "@vitejs/plugin-react";
import mdx from "fumadocs-mdx/vite";
import { defineConfig, type Plugin } from "vite";
import {
  resolveBuildQrInstallationVersions,
  shouldFetchQrInstallationVersions,
} from "#/components/qr/version.build";
import { CLIENT_ENV_PREFIX, env } from "#/lib/env";

type QrInstallationVersionsPromise = ReturnType<
  typeof resolveBuildQrInstallationVersions
>;

const qrInstallationVersionsCache = globalThis as typeof globalThis & {
  __webviewKioskQrInstallationVersionsPromise?: QrInstallationVersionsPromise;
};

function getQrInstallationVersions(): QrInstallationVersionsPromise {
  if (
    !qrInstallationVersionsCache.__webviewKioskQrInstallationVersionsPromise
  ) {
    qrInstallationVersionsCache.__webviewKioskQrInstallationVersionsPromise =
      resolveBuildQrInstallationVersions({
        enableNetwork: shouldFetchQrInstallationVersions(
          process.env.DOCS_FETCH_QR_VERSIONS,
        ),
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
    preview: {
      // The build's prerender server only needs loopback, not an external host.
      host: "127.0.0.1",
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
            retryCount: 3,
            retryDelay: 1000,
          },
        },
        pages: [
          {
            path: "/docs",
          },
          {
            path: "/debugger/unifiedpush",
          },
          {
            path: "/debugger/mqtt",
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
