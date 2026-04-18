#!/usr/bin/env bash
# Seed .env from .env.example when .env is missing.
#
# Intent:
#   - docker-compose.yml deliberately has NO default secrets (see audit-01 finding #1).
#   - Operators are expected to provide a populated .env.
#   - CI / verification harnesses and first-run developers get a zero-config path
#     here: a missing .env is auto-seeded from .env.example (placeholder values)
#     with a loud warning. Compose itself stays free of embedded secrets.
#
# Exit codes:
#   0  .env already present, OR successfully seeded from .env.example
#   1  neither .env nor .env.example exist (nothing we can do)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

if [ -f .env ]; then
  echo "bootstrap-env: .env already present; leaving it untouched."
  exit 0
fi

if [ ! -f .env.example ]; then
  echo "bootstrap-env: neither .env nor .env.example exist; cannot seed." >&2
  exit 1
fi

cp .env.example .env
chmod 600 .env

cat >&2 <<'WARN'
bootstrap-env: .env was missing and has been seeded from .env.example.
bootstrap-env: The seeded values are PLACEHOLDERS intended for local smoke tests,
bootstrap-env: CI/verification, and first-run developers only. They are NOT safe
bootstrap-env: for any shared or production-like environment. Rotate every value
bootstrap-env: in repo/.env before exposing the stack to anyone else.
WARN
