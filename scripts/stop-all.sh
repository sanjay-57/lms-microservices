#!/usr/bin/env bash
# Stops everything started by start-all.sh.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIDS="$ROOT/.pids"
[ -d "$PIDS" ] || { echo "Nothing to stop"; exit 0; }
for f in "$PIDS"/*.pid; do
  [ -e "$f" ] || continue
  pid=$(cat "$f"); name=$(basename "$f" .pid)
  if kill -0 "$pid" 2>/dev/null; then
    pkill -P "$pid" 2>/dev/null   # child processes (npm -> vite)
    kill "$pid" && echo "stopped $name ($pid)"
  fi
  rm -f "$f"
done
