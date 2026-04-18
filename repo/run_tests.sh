#!/bin/bash
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Seed .env from .env.example on a clean checkout. docker-compose.yml has its
# own labelled placeholder defaults (`INSECURE_DEFAULT_…`), so a missing .env
# is no longer fatal for compose itself; this seed gives `run_tests.sh` (and
# the API_tests/ harness) values like JOBOPS_ADMIN_PASSWORD that compose does
# not inject. Safe to run repeatedly — the helper is a no-op when .env exists.
if [ ! -f .env ] && [ -x scripts/bootstrap-env.sh ]; then
  scripts/bootstrap-env.sh || true
fi

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

# API_tests require JOBOPS_ADMIN_PASSWORD or BOOTSTRAP_ADMIN_PASSWORD (see API_tests/lib/test_env.sh).
# Do not inject default passwords here — set them in .env or the environment.

echo "======================================"
echo "  ShiftWorks JobOps — Test Runner"
echo "======================================"
echo ""

UNIT=0
FRONT=0
API_STATUS=0
API_FAIL_COUNT=0

# Helper: check if Docker is available and daemon is running
docker_available() {
  command -v docker &>/dev/null && docker info &>/dev/null 2>&1
}

# --- Backend unit tests ---
echo "=== Backend Unit Tests ==="
if docker_available; then
  echo "  Running backend tests via Docker (maven:3.9-eclipse-temurin-17-alpine)..."
  docker run --rm \
    -v "$(pwd)/backend:/workspace" \
    -w /workspace \
    maven:3.9-eclipse-temurin-17-alpine \
    mvn test -q 2>&1
  UNIT=$?
elif command -v mvn &>/dev/null && mvn -v >/dev/null 2>&1; then
  echo "  Docker not available — falling back to local mvn (Docker preferred)."
  (cd backend && mvn test -q 2>&1)
  UNIT=$?
elif [ -f backend/mvnw ] && (cd backend && ./mvnw -v >/dev/null 2>&1); then
  echo "  Docker not available — falling back to local mvnw (Docker preferred)."
  (cd backend && ./mvnw test -q 2>&1)
  UNIT=$?
else
  echo "  [SKIP] Neither Docker nor local Maven/JDK available."
  UNIT=2
fi
echo ""

# --- Frontend tests ---
echo "=== Frontend Tests ==="
if docker_available; then
  echo "  Running frontend tests via Docker (node:18-alpine)..."
  docker run --rm \
    -v "$(pwd)/frontend:/workspace" \
    -w /workspace \
    node:18-alpine \
    sh -c "npm ci --silent && npm test" 2>&1
  FRONT=$?
elif command -v node &>/dev/null; then
  echo "  Docker not available — falling back to local Node.js (Docker preferred)."
  if [ ! -d frontend/node_modules ]; then
    echo "  Installing frontend dependencies..."
    (cd frontend && npm install --silent 2>&1)
  fi
  (cd frontend && npm test 2>&1)
  FRONT=$?
else
  echo "  [SKIP] Neither Docker nor local Node.js available."
  FRONT=2
fi
echo ""

# --- API functional tests ---
echo "=== API Functional Tests ==="
if ! curl -s http://localhost:8080/api/auth/captcha -o /dev/null 2>/dev/null; then
  echo "  [SKIP] Backend not running at localhost:8080. Start with: docker compose up -d"
  API_STATUS=2
else
  FIRST=1
  for script in API_tests/test_*.sh; do
    [ -f "$script" ] || continue
    if [ $FIRST -eq 0 ]; then sleep 2; fi
    FIRST=0
    echo "--- Running $script ---"
    bash "$script"
    if [ $? -ne 0 ]; then
      API_FAIL_COUNT=$((API_FAIL_COUNT+1))
    fi
    echo ""
  done
  if [ $API_FAIL_COUNT -gt 0 ]; then
    API_STATUS=1
  fi
fi

# --- Summary ---
echo "======================================"
echo "  Summary"
echo "======================================"

if [ $UNIT -eq 0 ]; then echo "  Backend tests:   PASSED"
elif [ $UNIT -eq 2 ]; then echo "  Backend tests:   SKIPPED (mvn/Docker not found)"
else echo "  Backend tests:   FAILED"; fi

if [ $FRONT -eq 0 ]; then echo "  Frontend tests:  PASSED"
elif [ $FRONT -eq 2 ]; then echo "  Frontend tests:  SKIPPED (node/Docker not found)"
else echo "  Frontend tests:  FAILED"; fi

if [ $API_STATUS -eq 0 ]; then echo "  API tests:       PASSED"
elif [ $API_STATUS -eq 2 ]; then echo "  API tests:       SKIPPED (backend not running)"
else echo "  API tests:       FAILED ($API_FAIL_COUNT)"; fi

echo "======================================"

UNIT_OK=$( [ $UNIT -eq 0 ] || [ $UNIT -eq 2 ] && echo 1 || echo 0 )
FRONT_OK=$( [ $FRONT -eq 0 ] || [ $FRONT -eq 2 ] && echo 1 || echo 0 )
API_OK=$( [ $API_STATUS -eq 0 ] || [ $API_STATUS -eq 2 ] && echo 1 || echo 0 )
[ "$UNIT_OK" = "1" ] && [ "$FRONT_OK" = "1" ] && [ "$API_OK" = "1" ] && exit 0 || exit 1
