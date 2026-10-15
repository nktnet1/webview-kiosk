#!/bin/sh
# Compatibility entry point; export logic lives in the Python command.
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec python3 "$SCRIPT_DIR/export-android-test-assets.py" "$@"
