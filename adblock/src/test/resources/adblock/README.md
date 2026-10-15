# Network matching fixtures

`network.tsv` contains 128 hand-authored requests plus 256 deterministic generated
requests (seed 20261015) covering the supported network subset. The seven columns
identify the case, filter text (`\n` means a line break), request URL, source URL,
resource type, HTTP method, and expected blocking decision (`1` for block, `0` for
allow). `KotlinFilterEngineTest` runs every case against the matcher.

This corpus is regression evidence, not proof of compatibility with every rule in
EasyList or uBlock. Unsupported features and remaining WebView constraints are
listed in the repository's `docs/content-blocking.md`. Add meaningful request/rule pairs when fixing
matching bugs; derive expected results from the filter's meaning rather than
regenerating them from the implementation under test.
