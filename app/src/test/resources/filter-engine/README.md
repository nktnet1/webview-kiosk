# Network matching fixtures

`network.tsv` contains 128 hand-authored requests plus 256 deterministic generated
requests (seed 20261015) covering the supported network subset. Columns identify
the case, filter text (`\n` means a line break), request
URL, source URL, resource type, HTTP method, expected Kotlin decision, and the
recorded decision from the previous Brave `adblock` 0.13.3 build.

The reference was executed offline using the previous wrapper with default Cargo
features disabled and `embedded-domain-resolver` enabled. This was a one-time
migration check. Tests consume only the fixture; Rust is neither bundled nor
required to run tests or build an APK.

380 decisions agree. Four intentionally differ: `host-6` normalizes an uppercase
hostname, while `match-case-0`, `match-case-3`, and `match-case-4` exercise positive
case-sensitive literal matches. The old wrapper returned false for these inputs;
Kotlin expectations preserve hostname normalization and the declared `$match-case`
semantics. Those rows are not presented as reference parity.

This corpus is regression evidence, not proof of compatibility with every rule in
EasyList or uBlock. Unsupported features and remaining WebView constraints are
listed in `docs/content-blocking.md`. Add meaningful request/rule pairs when fixing
matching bugs; do not regenerate expectations from the Kotlin implementation.
