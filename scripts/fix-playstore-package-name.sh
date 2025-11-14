#!/bin/sh

current_branch="$(git rev-parse --abbrev-ref HEAD)"

if [ "$current_branch" != "playstore" ]; then
  echo "Error: You must be on the 'playstore' branch. Current branch is '$current_branch'."
  exit 1
fi

find . -type f \( -name "*.kt" -o -name "*.java" -o -name "*.xml" -o -name "*.gradle" -o -name "*.kts" \) \
  -exec sed -i 's/uk\.nktnet\.webviewkiosk/com.nktnet.webview_kiosk/g' {} +
