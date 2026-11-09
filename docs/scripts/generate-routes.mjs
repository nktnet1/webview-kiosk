import { Generator, getConfig } from "@tanstack/router-generator";

const root = process.cwd();
const config = getConfig(
  {
    // Typecheck runs before Vite in CI, so routes must be regenerated here.
    // Match the type registration appended by TanStack Start's Vite plugin.
    routeTreeFileFooter: [
      `import type { getRouter } from './router.tsx'
import type { createStart } from '@tanstack/react-start'
declare module '@tanstack/react-start' {
  interface Register {
    ssr: true
    router: Awaited<ReturnType<typeof getRouter>>
  }
}`,
    ],
  },
  root,
);
await new Generator({ config, root }).run();
