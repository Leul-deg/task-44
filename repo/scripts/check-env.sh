#!/usr/bin/env bash
# Validate required variables before `docker compose up`.
# Note: docker-compose.yml has labelled placeholder defaults that let compose
# boot without .env on cloud CI, but those defaults are NOT acceptable for any
# shared environment. This script enforces the real requirement: every secret
# must be set explicitly (either in .env or in the host shell) AND
# AES_SECRET_KEY must decode to exactly 32 bytes.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

fatal() {
  echo "check-env: $*" >&2
  exit 1
}

[ -n "${MYSQL_ROOT_PASSWORD:-}" ] || fatal "MYSQL_ROOT_PASSWORD is unset or empty (set in repo/.env)"
[ -n "${DB_PASSWORD:-}" ] || fatal "DB_PASSWORD is unset or empty (set in repo/.env)"
[ -n "${BOOTSTRAP_ADMIN_PASSWORD:-}" ] || fatal "BOOTSTRAP_ADMIN_PASSWORD is unset or empty (set in repo/.env)"
[ -n "${AES_SECRET_KEY:-}" ] || fatal "AES_SECRET_KEY is unset or empty (set in repo/.env)"

python3 <<'PY' || fatal "AES_SECRET_KEY must be 32 UTF-8 bytes (32 ASCII chars) or standard Base64 decoding to exactly 32 bytes"
import base64, os, sys
raw = os.environ.get("AES_SECRET_KEY", "").strip()
if not raw:
    sys.exit("empty AES_SECRET_KEY")
if len(raw) == 32:
    key = raw.encode("utf-8")
else:
    try:
        key = base64.b64decode(raw)
    except Exception as e:
        sys.exit(f"invalid Base64: {e}")
if len(key) != 32:
    sys.exit(f"AES key material must be 32 bytes, got {len(key)}")
PY

echo "check-env: required secrets are set and AES_SECRET_KEY length is valid."
