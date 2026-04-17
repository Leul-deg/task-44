#!/bin/bash
BASE=http://localhost:8080/api
PASS=0; FAIL=0
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/test_env.sh"
require_admin_password

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "PASS: $desc"; PASS=$((PASS+1))
  else
    echo "FAIL: $desc (expected=$expected, got=$actual)"; FAIL=$((FAIL+1))
  fi
}

# Authenticate as admin
LOGIN_CODE=$(curl -s -o /tmp/dict_login.json -w "%{http_code}" -c /tmp/dict_cookies.txt \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "$(admin_login_json)")
check "Login returns 200" "200" "$LOGIN_CODE"
CSRF=$(python3 -c "import json; print(json.load(open('/tmp/dict_login.json')).get('csrfToken',''))" 2>/dev/null)

# Test 1: GET /api/categories — public endpoint
CODE=$(curl -s -o /tmp/dict_categories.json -w "%{http_code}" "$BASE/categories")
check "GET /api/categories returns 200" "200" "$CODE"
BODY=$(cat /tmp/dict_categories.json)
echo "$BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); exit(0 if isinstance(d,list) else 1)" 2>/dev/null \
  && echo "PASS: categories is array" && PASS=$((PASS+1)) \
  || { echo "FAIL: categories is not array"; FAIL=$((FAIL+1)); }

# Test 2: GET /api/locations — public endpoint
CODE=$(curl -s -o /tmp/dict_locations.json -w "%{http_code}" "$BASE/locations")
check "GET /api/locations returns 200" "200" "$CODE"
BODY=$(cat /tmp/dict_locations.json)
echo "$BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); exit(0 if isinstance(d,list) else 1)" 2>/dev/null \
  && echo "PASS: locations is array" && PASS=$((PASS+1)) \
  || { echo "FAIL: locations is not array"; FAIL=$((FAIL+1)); }

# Test 3: GET /api/locations/states — public endpoint
CODE=$(curl -s -o /tmp/dict_states.json -w "%{http_code}" "$BASE/locations/states")
check "GET /api/locations/states returns 200" "200" "$CODE"
BODY=$(cat /tmp/dict_states.json)
echo "$BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); exit(0 if isinstance(d,list) else 1)" 2>/dev/null \
  && echo "PASS: states is array" && PASS=$((PASS+1)) \
  || { echo "FAIL: states is not array"; FAIL=$((FAIL+1)); }

# Test 4: GET /api/admin/categories — admin only
CODE=$(curl -s -o /tmp/admin_categories.json -w "%{http_code}" -b /tmp/dict_cookies.txt \
  -H "X-XSRF-TOKEN: $CSRF" "$BASE/admin/categories")
check "GET /api/admin/categories returns 200" "200" "$CODE"
BODY=$(cat /tmp/admin_categories.json)
echo "$BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); exit(0 if isinstance(d,list) else 1)" 2>/dev/null \
  && echo "PASS: admin categories is array" && PASS=$((PASS+1)) \
  || { echo "FAIL: admin categories is not array"; FAIL=$((FAIL+1)); }

# Test 5: GET /api/admin/locations — admin only
CODE=$(curl -s -o /tmp/admin_locations.json -w "%{http_code}" -b /tmp/dict_cookies.txt \
  -H "X-XSRF-TOKEN: $CSRF" "$BASE/admin/locations")
check "GET /api/admin/locations returns 200" "200" "$CODE"
BODY=$(cat /tmp/admin_locations.json)
echo "$BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); exit(0 if isinstance(d,list) else 1)" 2>/dev/null \
  && echo "PASS: admin locations is array" && PASS=$((PASS+1)) \
  || { echo "FAIL: admin locations is not array"; FAIL=$((FAIL+1)); }

# Test 6: GET /api/admin/categories unauthenticated — must be 403
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/admin/categories")
check "Admin categories unauthenticated returns 403" "403" "$CODE"

# Test 7: GET /api/admin/stats/counts — admin only
CODE=$(curl -s -o /tmp/admin_stats.json -w "%{http_code}" -b /tmp/dict_cookies.txt \
  -H "X-XSRF-TOKEN: $CSRF" "$BASE/admin/stats/counts")
check "GET /api/admin/stats/counts returns 200" "200" "$CODE"
BODY=$(cat /tmp/admin_stats.json)
echo "$BODY" | grep -q '"totalUsers"' \
  && echo "PASS: stats response has totalUsers" && PASS=$((PASS+1)) \
  || { echo "FAIL: stats response missing totalUsers"; FAIL=$((FAIL+1)); }

echo ""
echo "Results: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
