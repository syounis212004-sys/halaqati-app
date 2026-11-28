#!/usr/bin/env bash
set -euo pipefail
# Report activity without logging command lines, credentials or environment variables.
echo "Gradle stage: $*"
free -h
(
  while sleep 30; do
    printf '\n[build-health] %s\n' "$(date -u +%FT%TZ)"
    free -m
    ps -eo pid,comm,rss,pcpu --sort=-rss | awk 'NR==1 || ($2 ~ /^(java|kotlinc)$/ && ++n<=8)'
  done
) &
monitor=$!
trap 'kill "$monitor" 2>/dev/null || true; wait "$monitor" 2>/dev/null || true' EXIT
gradle --profile --stacktrace "$@"
