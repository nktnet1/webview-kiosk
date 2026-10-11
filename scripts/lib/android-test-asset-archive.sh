#!/usr/bin/env bash
# Stream an archive to one file when it is below the upload limit; otherwise
# retain numbered parts. Avoid buffering the entire compressed archive on disk.
export_test_asset_archive() {
    local archive_path="$1"
    local max_bytes="$2"
    local part_prefix="${archive_path}.part-"

    ((max_bytes > 1)) || {
        printf 'Invalid archive size limit: %s\n' "$max_bytes" >&2
        return 1
    }
    # Make every emitted file strictly smaller than the upload limit.
    split -b "$((max_bytes - 1))" -d -a 3 - "$part_prefix" || return 1
    [[ -f "${part_prefix}000" ]] || {
        printf 'No archive data was produced: %s\n' "$archive_path" >&2
        return 1
    }
    if [[ ! -f "${part_prefix}001" ]]; then
        mv -- "${part_prefix}000" "$archive_path"
    fi
}
