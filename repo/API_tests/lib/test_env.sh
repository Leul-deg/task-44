#!/bin/bash

JOBOPS_ADMIN_USERNAME="${JOBOPS_ADMIN_USERNAME:-admin}"
JOBOPS_ADMIN_PASSWORD="${JOBOPS_ADMIN_PASSWORD:-${BOOTSTRAP_ADMIN_PASSWORD:-}}"

require_admin_password() {
  if [ -z "$JOBOPS_ADMIN_PASSWORD" ]; then
    echo "FATAL: Set JOBOPS_ADMIN_PASSWORD (or BOOTSTRAP_ADMIN_PASSWORD) before running API tests." >&2
    exit 2
  fi
}

admin_login_json() {
  printf '{"username":"%s","password":"%s"}' "$JOBOPS_ADMIN_USERNAME" "$JOBOPS_ADMIN_PASSWORD"
}
