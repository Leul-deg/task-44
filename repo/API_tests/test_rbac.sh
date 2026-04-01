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

# Create employer user via admin
ADMIN_OUT=$(curl -s -c /tmp/rbac_admin.txt \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456789"}')
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
CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/rbac_emp.txt \
  -X GET "$BASE/admin/users")
check "Employer cannot access admin/users → 403" "403" "$CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/rbac_emp.txt \
  -X GET "$BASE/review/queue")
check "Employer cannot access review/queue → 403" "403" "$CODE"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -b /tmp/rbac_emp.txt \
  -X GET "$BASE/admin/audit-logs")
check "Employer cannot access audit-logs → 403" "403" "$CODE"

echo ""
echo "Results: $PASS passed, $FAIL failed"
rm -f /tmp/rbac_admin.txt /tmp/rbac_emp.txt
exit $FAIL
