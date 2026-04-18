#!/usr/bin/env bash
# Seed .env from .env.example when .env is missing.
#
# Intent:
#   - docker-compose.yml carries clearly-labelled placeholder defaults
#     (INSECURE_DEFAULT_… / INSECURE_32B_DEFAULT_…) so it boots on cloud CI
#     even with no .env on disk. See audit-01 fix-check item #1 for the
#     trade-off rationale.
#   - This helper still seeds .env from .env.example for tooling that does
#     NOT read compose substitutions (most importantly run_tests.sh and the
#     API_tests/ harness, which need JOBOPS_ADMIN_PASSWORD /
#     BOOTSTRAP_ADMIN_PASSWORD in the host shell).
#   - Operators should still create a real .env (replacing every placeholder)
#     for any shared or production-like environment.
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
