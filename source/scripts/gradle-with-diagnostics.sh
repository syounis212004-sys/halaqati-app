#!/usr/bin/env bash
set -euo pipefail
# Report activity without logging command lines, credentials or environment variables.
echo "Gradle stage: $*"
free -h
(
  sample=0
  while sleep 30; do
    printf '\n[build-health] %s\n' "$(date -u +%FT%TZ)"
    free -m
    ps -eo pid,comm,rss,pcpu --sort=-rss | awk 'NR==1 || ($2 ~ /^(java|kotlinc)$/ && ++n<=8)'
    sample=$((sample + 1))
    if [ $((sample % 6)) -eq 0 ] && [ -n "${RUNNER_TEMP:-}" ]; then
      pid=$(ps -eo pid,comm,rss --sort=-rss | awk '$2 == "java" && $3 > 1048576 {print $1; exit}')
      if [ -n "$pid" ]; then
        timeout 20s jcmd "$pid" Thread.print > "$RUNNER_TEMP/gradle-threads-$sample.log" 2>&1 || true
      fi
    fi
  done
) &
monitor=$!
trap 'kill "$monitor" 2>/dev/null || true; wait "$monitor" 2>/dev/null || true' EXIT
gradle --profile --stacktrace "$@"
