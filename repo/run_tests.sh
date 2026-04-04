#!/bin/bash
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

echo "======================================"
echo "  ShiftWorks JobOps — Test Runner"
echo "======================================"
echo ""

UNIT=0
FRONT=0
API_FAIL=0
API_SKIP=0

# --- Backend unit tests ---
echo "=== Backend Unit Tests ==="
if command -v mvn &>/dev/null && mvn -version &>/dev/null 2>&1; then
  (cd backend && mvn test -q 2>&1)
  UNIT=$?
elif [ -f backend/mvnw ] && (cd backend && ./mvnw -version &>/dev/null 2>&1); then
  (cd backend && ./mvnw test -q 2>&1)
  UNIT=$?
elif command -v docker &>/dev/null; then
  echo "  Running via Docker (maven:3.9.6-eclipse-temurin-17)..."
  docker run --rm \
    -v "$(pwd)/backend:/app" \
    -v "$HOME/.m2:/root/.m2" \
    -w /app \
    maven:3.9.6-eclipse-temurin-17 \
    mvn test -q 2>&1
  UNIT=$?
else
  echo "  [SKIP] Maven/Java/Docker not available. Install JDK 17 + Maven, or Docker."
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
CURL_CHECK="curl -s --max-time 3 http://localhost:8080/api/auth/captcha -o /dev/null 2>/dev/null"
if ! eval "$CURL_CHECK"; then
  if command -v docker &>/dev/null; then
    echo "  Backend not running — starting Docker stack..."
    docker compose up -d 2>&1
    echo "  Waiting for backend to be ready (up to 90s)..."
    for i in $(seq 1 45); do
      eval "$CURL_CHECK" && break
      sleep 2
    done
    if ! eval "$CURL_CHECK"; then
      echo "  [SKIP] Backend did not become ready in time."
      API_SKIP=1
    fi
  else
    echo "  [SKIP] Backend not running and Docker not available. Start with: docker compose up -d"
    API_SKIP=1
  fi
fi

if [ $API_SKIP -eq 0 ]; then
  FIRST=1
  for script in API_tests/test_*.sh; do
    [ -f "$script" ] || continue
    if [ $FIRST -eq 0 ]; then sleep 2; fi
    FIRST=0
    echo "--- Running $script ---"
    bash "$script"
    if [ $? -ne 0 ]; then
      API_FAIL=$((API_FAIL+1))
    fi
    echo ""
  done
fi

# --- Summary ---
echo "======================================"
echo "  Summary"
echo "======================================"

if [ $UNIT -eq 0 ]; then echo "  Backend tests:   PASSED"
elif [ $UNIT -eq 2 ]; then echo "  Backend tests:   SKIPPED (no Maven/Java/Docker)"
else echo "  Backend tests:   FAILED"; fi

if [ $FRONT -eq 0 ]; then echo "  Frontend tests:  PASSED"
elif [ $FRONT -eq 2 ]; then echo "  Frontend tests:  SKIPPED (node not found)"
else echo "  Frontend tests:  FAILED"; fi

if [ $API_SKIP -eq 1 ]; then echo "  API tests:       SKIPPED (backend not ready)"
elif [ $API_FAIL -eq 0 ]; then echo "  API tests:       PASSED"
else echo "  API tests:       FAILED ($API_FAIL)"; fi

echo "======================================"

UNIT_OK=$( [ $UNIT -eq 0 ] || [ $UNIT -eq 2 ] && echo 1 || echo 0 )
FRONT_OK=$( [ $FRONT -eq 0 ] || [ $FRONT -eq 2 ] && echo 1 || echo 0 )
API_OK=$( [ $API_SKIP -eq 1 ] || [ $API_FAIL -eq 0 ] && echo 1 || echo 0 )
[ "$UNIT_OK" = "1" ] && [ "$FRONT_OK" = "1" ] && [ "$API_OK" = "1" ] && exit 0 || exit 1
