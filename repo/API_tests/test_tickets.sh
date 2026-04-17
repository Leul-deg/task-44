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
LOGIN_CODE=$(curl -s -o /tmp/tkt_login.json -w "%{http_code}" -c /tmp/tkt_cookies.txt \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "$(admin_login_json)")
check "Login returns 200" "200" "$LOGIN_CODE"
CSRF=$(python3 -c "import json; print(json.load(open('/tmp/tkt_login.json')).get('csrfToken',''))" 2>/dev/null)

# Test 1: Create a ticket
CREATE_CODE=$(curl -s -o /tmp/tkt_create.json -w "%{http_code}" -b /tmp/tkt_cookies.txt \
  -X POST "$BASE/tickets" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"subject":"API test ticket","description":"Created during automated test","priority":"LOW"}')
check "POST /api/tickets returns 200" "200" "$CREATE_CODE"
BODY=$(cat /tmp/tkt_create.json)
echo "$BODY" | grep -q '"id"' \
  && echo "PASS: create ticket response has id" && PASS=$((PASS+1)) \
  || { echo "FAIL: create ticket response missing id"; FAIL=$((FAIL+1)); }
echo "$BODY" | grep -q '"subject"' \
  && echo "PASS: create ticket response has subject" && PASS=$((PASS+1)) \
  || { echo "FAIL: create ticket response missing subject"; FAIL=$((FAIL+1)); }
TKT_ID=$(python3 -c "import json; print(json.load(open('/tmp/tkt_create.json')).get('id',''))" 2>/dev/null)

# Test 2: List tickets
LIST_CODE=$(curl -s -o /tmp/tkt_list.json -w "%{http_code}" -b /tmp/tkt_cookies.txt \
  -H "X-XSRF-TOKEN: $CSRF" "$BASE/tickets")
check "GET /api/tickets returns 200" "200" "$LIST_CODE"
BODY=$(cat /tmp/tkt_list.json)
echo "$BODY" | grep -q '"totalElements"\|"items"' \
  && echo "PASS: list tickets response has pagination" && PASS=$((PASS+1)) \
  || { echo "FAIL: list tickets response missing pagination"; FAIL=$((FAIL+1)); }

# Test 3: Get ticket detail
if [ -n "$TKT_ID" ]; then
  DETAIL_CODE=$(curl -s -o /tmp/tkt_detail.json -w "%{http_code}" -b /tmp/tkt_cookies.txt \
    -H "X-XSRF-TOKEN: $CSRF" "$BASE/tickets/$TKT_ID")
  check "GET /api/tickets/{id} returns 200" "200" "$DETAIL_CODE"
  BODY=$(cat /tmp/tkt_detail.json)
  echo "$BODY" | grep -q '"status"' \
    && echo "PASS: ticket detail has status" && PASS=$((PASS+1)) \
    || { echo "FAIL: ticket detail missing status"; FAIL=$((FAIL+1)); }
fi

# Test 4: Update ticket (admin only)
if [ -n "$TKT_ID" ]; then
  UPDATE_CODE=$(curl -s -o /tmp/tkt_update.json -w "%{http_code}" -b /tmp/tkt_cookies.txt \
    -X PUT "$BASE/tickets/$TKT_ID" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
    -d '{"status":"IN_PROGRESS","priority":"MEDIUM","resolution":""}')
  check "PUT /api/tickets/{id} as admin returns 200" "200" "$UPDATE_CODE"
  BODY=$(cat /tmp/tkt_update.json)
  echo "$BODY" | grep -q '"status"' \
    && echo "PASS: update ticket response has status" && PASS=$((PASS+1)) \
    || { echo "FAIL: update ticket response missing status"; FAIL=$((FAIL+1)); }
fi

# Test 5: Unauthenticated ticket list must return 403
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/tickets")
check "Unauthenticated GET /api/tickets returns 403" "403" "$CODE"

echo ""
echo "Results: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
