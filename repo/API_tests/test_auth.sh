#!/bin/bash
BASE=http://localhost:8080/api
PASS=0; FAIL=0

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
  -d '{"username":"admin","password":"Admin@123456789"}')
CSRF=$(python3 -c "import json; print(json.load(open('/tmp/auth_login.json')).get('csrfToken',''))" 2>/dev/null)
check "Valid login returns 200" "200" "$LOGIN_CODE"

# Test 2: Invalid password
CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrongpassword"}')
check "Invalid password returns 401" "401" "$CODE"

# Test 3: Admin creates user (registration is admin-only)
UNIQUE="testuser_$(date +%s)"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/auth_cookies.txt \
  -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d "{\"username\":\"$UNIQUE\",\"email\":\"$UNIQUE@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}")
check "Admin creates user returns 200" "200" "$CODE"

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
rm -f /tmp/auth_cookies.txt /tmp/auth_login.json
exit $FAIL
