# Adblock

Internal Kotlin/JVM library for the kiosk's network and basic CSS filter rules.
It depends on the same OkHttp version as the app for URL parsing and public-suffix
classification. It has no Android, Compose, or app-module dependency.

The public API is in `uk.nktnet.webviewkiosk.adblock`:

- `KotlinFilterEngine` validates and compiles lists, matches requests, and selects
  cosmetic rules. Optional `cacheFile` and `cacheVersion` arguments enable the
  compiled disk cache. Construction does not read lists or load the cache.
- `NetworkFilterEngine` and `FilterListValidator` are the app's injectable boundaries.
- `RuleLoadStage` reports loading progress.
- `inspectFilterList` counts saved rules without changing an engine snapshot.
- `validateFilterFileFormat` checks plain-text integrity independently of syntax validation.

Call validation, inspection, and `replace` on a worker thread. `replace` publishes
one immutable snapshot only after compilation succeeds; request and selector readers
continue to use the previous snapshot while a replacement is built. The app owns
serialization of replacements, list storage, downloads, and WebView integration.

From the repository root, run the plain JVM tests with:

```sh
./gradlew :adblock:test --configure-on-demand
```

The tests require no Android SDK or Robolectric. Run the complete engine and Android
suite with `sh scripts/ci/test-local.sh jvm`. See `docs/content-blocking.md` for the
supported syntax, resource limits, and WebView limitations.
