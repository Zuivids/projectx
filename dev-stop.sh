#!/usr/bin/env bash
# Stops the local system. The database keeps its data (reset it with ./dev-reset-db.sh).
set -uo pipefail
cd "$(dirname "$0")"

# Stops whatever listens on a port: the backend JVM (8080) and the Vite server (5173)
stop_port() {
  local port=$1 name=$2 pids=""
  if command -v lsof >/dev/null; then
    pids=$(lsof -ti "tcp:$port" -sTCP:LISTEN || true)
    [ -n "$pids" ] && kill $pids
  elif command -v netstat >/dev/null; then
    # Windows (Git Bash): netstat shows Windows process ids
    pids=$(netstat -ano | awk -v p=":$port" '$2 ~ p"$" && $4 == "LISTENING" {print $5}' | sort -u)
    for pid in $pids; do taskkill //F //T //PID "$pid" >/dev/null 2>&1; done
  fi
  if [ -n "$pids" ]; then echo "Stopped $name."; else echo "$name was not running."; fi
}

stop_port 5173 "frontend"
stop_port 8080 "backend"

echo "Stopping database..."
docker compose stop
echo "Stopped. Data is kept; start again with ./dev-start.sh"
