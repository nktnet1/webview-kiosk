# Network matching fixtures

`network.tsv` is read by `KotlinFilterEngineTest`. Its columns are case ID,
filter text, request URL, source URL, resource type, HTTP method, and expected
decision (`1` for block, `0` for allow). In filter text, `\n` means a line break.
