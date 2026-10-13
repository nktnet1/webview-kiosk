# Webview Kiosk documentation

The documentation site is built with TanStack Start and Fumadocs.

Use Node.js 24 and the pnpm version specified in `package.json`.

To run the development server from this directory:

```bash
pnpm dev
```

Pages are in `content/docs/`. Keep the descriptions consistent with the
corresponding setting help in the Android app.

After installing dependencies, `pnpm build` checks GitHub, F-Droid and
IzzyOnDroid for QR installation versions by default. Failed lookups are
retried twice, then use bundled version fallbacks so an offline build can still
finish. Set `DOCS_FETCH_QR_VERSIONS=0` to skip external requests. Prerendering
still requires access to the local loopback interface.
