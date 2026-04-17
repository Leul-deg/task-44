#!/bin/bash
BASE=http://localhost:8080/api
PASS=0; FAIL=0
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/test_env.sh"
require_admin_password

jp() { python3 -c "import sys,json; print(json.load(sys.stdin).get('$1',''))" 2>/dev/null; }

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "PASS: $desc"; PASS=$((PASS+1))
  else
    echo "FAIL: $desc (expected=$expected, got=$actual)"; FAIL=$((FAIL+1))
  fi
}

# Test 1: Valid admin login (single call for cookie + CSRF + HTTP code)
LOGIN_CODE=$(curl -s -o /tmp/auth_login.json -w "%{http_code}" -c /tmp/auth_cookies.txt \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "$(admin_login_json)")
CSRF=$(python3 -c "import json; print(json.load(open('/tmp/auth_login.json')).get('csrfToken',''))" 2>/dev/null)
check "Valid login returns 200" "200" "$LOGIN_CODE"

# Validate login response payload fields
LOGIN_BODY=$(cat /tmp/auth_login.json)
echo "$LOGIN_BODY" | grep -q '"csrfToken"' || { echo "FAIL: login response missing csrfToken"; FAIL=$((FAIL+1)); }
echo "$LOGIN_BODY" | grep -q '"user"' || { echo "FAIL: login response missing user object"; FAIL=$((FAIL+1)); }
echo "$LOGIN_BODY" | grep -q '"role"' || { echo "FAIL: login response user missing role field"; FAIL=$((FAIL+1)); }

# Test 2: Invalid password
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrongpassword"}')
check "Invalid password returns 401" "401" "$CODE"

# Test 3: Admin creates user (registration is admin-only)
UNIQUE="testuser_$(date +%s)"
CREATE_USER_BODY=$(curl -s -o /tmp/auth_create_user.json -w "%{http_code}" -b /tmp/auth_cookies.txt \
  -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d "{\"username\":\"$UNIQUE\",\"email\":\"$UNIQUE@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}")
CODE="$CREATE_USER_BODY"
check "Admin creates user returns 200" "200" "$CODE"

# Validate create user response payload fields
USER_RESP=$(cat /tmp/auth_create_user.json 2>/dev/null)
echo "$USER_RESP" | grep -q '"id"' || { echo "FAIL: create user response missing id field"; FAIL=$((FAIL+1)); }
echo "$USER_RESP" | grep -q '"username"' || { echo "FAIL: create user response missing username field"; FAIL=$((FAIL+1)); }

# Test 4: Weak password rejected
CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/auth_cookies.txt \
  -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"username":"weakuser","email":"weak@test.com","password":"short","role":"EMPLOYER"}')
check "Weak password returns 400" "400" "$CODE"

# Test 5: Duplicate username
CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/auth_cookies.txt \
  -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d "{\"username\":\"$UNIQUE\",\"email\":\"dup@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}")
check "Duplicate username returns 409" "409" "$CODE"

# Test 6: Unauthenticated register is blocked
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"rogue","email":"rogue@test.com","password":"StrongPass123!"}')
check "Public register blocked returns 403" "403" "$CODE"

# Test 7: Logout then access protected endpoint
curl -s -o /dev/null -b /tmp/auth_cookies.txt -X POST "$BASE/auth/logout" -H "X-XSRF-TOKEN: $CSRF"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/auth_cookies.txt \
  -X GET "$BASE/admin/users")
if [ "$CODE" = "401" ] || [ "$CODE" = "403" ]; then
  echo "PASS: After logout access denied (HTTP $CODE)"; PASS=$((PASS+1))
else
  echo "FAIL: After logout not denied (expected 401/403, got $CODE)"; FAIL=$((FAIL+1))
fi

echo ""
echo "Results: $PASS passed, $FAIL failed"
rm -f /tmp/auth_cookies.txt /tmp/auth_login.json /tmp/auth_create_user.json
exit $FAIL
