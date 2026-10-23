#!/usr/bin/env bash
# Deletes the LOCAL database with all its data. The next ./dev-start.sh creates a fresh one,
# including the local super admin.
set -euo pipefail
cd "$(dirname "$0")"

read -r -p "Delete ALL local data (users, courses)? Type YES to continue: " answer
[ "$answer" = "YES" ] || { echo "Cancelled."; exit 0; }

./dev-stop.sh
docker compose down -v
echo "Local database deleted. Run ./dev-start.sh to start with a fresh one."
