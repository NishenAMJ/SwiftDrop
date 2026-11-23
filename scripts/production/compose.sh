#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"
# Always use the same project name and files, including for recovery commands.
exec docker compose --project-name "${PROD_PROJECT:-swiftdrop-prod}" \
  --env-file "${PROD_ENV_FILE:-$repo_root/.env.production}" \
  -f compose.yaml -f compose.production.yaml "$@"
