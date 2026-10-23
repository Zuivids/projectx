#!/usr/bin/env bash
# Builds and tests everything a deployment ships, into release/:
#   release/projectx.jar   backend (run with: java -jar projectx.jar)
#   release/frontend/      static files of the React app
# Deploying them to a server waits for the hosting decision (BACKLOG D-05, D-09).
set -euo pipefail
cd "$(dirname "$0")"

docker info >/dev/null 2>&1 || { echo "ERROR: Docker must be running (the tests use it)." >&2; exit 1; }

echo "Building and testing the backend..."
./mvnw -B -q clean verify

echo "Building and testing the frontend..."
(cd frontend && npm ci --no-fund && npm run lint && npm test && npm run build)

rm -rf release
mkdir -p release
cp target/projectx-*.jar release/projectx.jar
cp -r frontend/dist release/frontend
git rev-parse --short HEAD > release/VERSION 2>/dev/null || true

echo "Release ready in release/ (version $(cat release/VERSION 2>/dev/null || echo unknown))."
