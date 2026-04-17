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

# Create employer user via admin
ADMIN_OUT=$(curl -s -c /tmp/rbac_admin.txt \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "$(admin_login_json)")
CSRF_ADMIN=$(echo "$ADMIN_OUT" | jp csrfToken)

EMPLOYER="rbac_emp_$(date +%s)"
curl -s -o /dev/null -b /tmp/rbac_admin.txt \
  -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_ADMIN" \
  -d "{\"username\":\"$EMPLOYER\",\"email\":\"$EMPLOYER@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}"

# Login as employer
curl -s -o /dev/null -c /tmp/rbac_emp.txt \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$EMPLOYER\",\"password\":\"StrongPass123!\"}"

# Employer tries admin endpoints
RESP=$(curl -s -w "\n%{http_code}" -b /tmp/rbac_emp.txt -X GET "$BASE/admin/users")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n 1)
check "Employer cannot access admin/users → 403" "403" "$CODE"
echo "$BODY" | grep -qE '"message"|"error"|"status"' || { echo "FAIL: 403 response for admin/users missing error field"; FAIL=$((FAIL+1)); }

RESP=$(curl -s -w "\n%{http_code}" -b /tmp/rbac_emp.txt -X GET "$BASE/review/queue")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n 1)
check "Employer cannot access review/queue → 403" "403" "$CODE"
echo "$BODY" | grep -qE '"message"|"error"|"status"' || { echo "FAIL: 403 response for review/queue missing error field"; FAIL=$((FAIL+1)); }

RESP=$(curl -s -w "\n%{http_code}" -b /tmp/rbac_emp.txt -X GET "$BASE/admin/audit-logs")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n 1)
check "Employer cannot access audit-logs → 403" "403" "$CODE"
echo "$BODY" | grep -qE '"message"|"error"|"status"' || { echo "FAIL: 403 response for audit-logs missing error field"; FAIL=$((FAIL+1)); }

echo ""
echo "Results: $PASS passed, $FAIL failed"
rm -f /tmp/rbac_admin.txt /tmp/rbac_emp.txt
exit $FAIL
