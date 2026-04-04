#!/bin/bash
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

# Keep API test credentials aligned with docker-compose defaults when
# no explicit environment is provided by CI.
: "${BOOTSTRAP_ADMIN_PASSWORD:=ShiftAdmin!2026#Strong}"
: "${JOBOPS_ADMIN_PASSWORD:=$BOOTSTRAP_ADMIN_PASSWORD}"
export BOOTSTRAP_ADMIN_PASSWORD JOBOPS_ADMIN_PASSWORD

echo "======================================"
echo "  ShiftWorks JobOps — Test Runner"
echo "======================================"
echo ""

UNIT=0
FRONT=0
API_STATUS=0
API_FAIL_COUNT=0

# --- Backend unit tests ---
echo "=== Backend Unit Tests ==="
if command -v mvn &>/dev/null && mvn -v >/dev/null 2>&1; then
  (cd backend && mvn test -q 2>&1)
  UNIT=$?
elif [ -f backend/mvnw ] && (cd backend && ./mvnw -v >/dev/null 2>&1); then
  (cd backend && ./mvnw test -q 2>&1)
  UNIT=$?
else
  echo "  [SKIP] Maven unavailable locally (JAVA_HOME/JDK issue)."
  UNIT=2
fi
echo ""

# --- Frontend tests ---
echo "=== Frontend Tests ==="
if command -v node &>/dev/null; then
  if [ ! -d frontend/node_modules ]; then
    echo "  Installing frontend dependencies..."
    (cd frontend && npm install --silent 2>&1)
  fi
  (cd frontend && npm test 2>&1)
  FRONT=$?
else
  echo "  [SKIP] Node.js not found. Install Node 18+ or use Docker."
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
elif [ $UNIT -eq 2 ]; then echo "  Backend tests:   SKIPPED (mvn not found)"
else echo "  Backend tests:   FAILED"; fi

if [ $FRONT -eq 0 ]; then echo "  Frontend tests:  PASSED"
elif [ $FRONT -eq 2 ]; then echo "  Frontend tests:  SKIPPED (node not found)"
else echo "  Frontend tests:  FAILED"; fi

if [ $API_STATUS -eq 0 ]; then echo "  API tests:       PASSED"
elif [ $API_STATUS -eq 2 ]; then echo "  API tests:       SKIPPED (backend not running)"
else echo "  API tests:       FAILED ($API_FAIL_COUNT)"; fi

echo "======================================"

UNIT_OK=$( [ $UNIT -eq 0 ] || [ $UNIT -eq 2 ] && echo 1 || echo 0 )
FRONT_OK=$( [ $FRONT -eq 0 ] || [ $FRONT -eq 2 ] && echo 1 || echo 0 )
API_OK=$( [ $API_STATUS -eq 0 ] || [ $API_STATUS -eq 2 ] && echo 1 || echo 0 )
[ "$UNIT_OK" = "1" ] && [ "$FRONT_OK" = "1" ] && [ "$API_OK" = "1" ] && exit 0 || exit 1
