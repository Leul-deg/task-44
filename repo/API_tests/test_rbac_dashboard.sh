#!/bin/bash
BASE="http://localhost:8080"
FAIL=0
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/lib/test_env.sh"
require_admin_password

check() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then echo "  ✓ $label (HTTP $actual)"
  else echo "  ✗ $label — expected $expected, got $actual"; FAIL=$((FAIL+1)); fi
}

echo "============================================="
echo "  RBAC TEST: EMPLOYER BLOCKED FROM DASHBOARDS"
echo "============================================="

ADMIN_CSRF=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "$(admin_login_json)" -c /tmp/admin.txt \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('csrfToken',''))" 2>/dev/null)

EMPLOYER_USER="rbac_dash_emp_$(date +%s)"
curl -s -o /dev/null -b /tmp/admin.txt -X POST "$BASE/api/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"email\":\"$EMPLOYER_USER@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}"

# Login as employer
EMP_CSRF=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"password\":\"StrongPass123!\"}" -c /tmp/emp.txt \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('csrfToken',''))" 2>/dev/null)

if [ -z "$EMP_CSRF" ]; then echo "FATAL: employer1 login failed"; exit 1; fi

echo -e "\n[1] Employer GET /api/dashboards → 403"
RESP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/dashboards" -b /tmp/emp.txt)
check "List dashboards" "403" "$RESP"

echo -e "\n[2] Employer POST /api/dashboards → 403"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/dashboards" -b /tmp/emp.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF" \
  -d '{"name":"hack","metricsJson":["post_volume"],"dimensionsJson":"date_daily","filtersJson":{}}')
check "Create dashboard" "403" "$RESP"

echo -e "\n[3] Employer POST /api/dashboards/preview → 403"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/dashboards/preview" -b /tmp/emp.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF" \
  -d '{"metricsJson":["post_volume"],"dimensionsJson":"date_daily","filtersJson":{}}')
check "Preview dashboard" "403" "$RESP"

echo -e "\n[4] Employer POST /api/dashboards/1/export → 403"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/dashboards/1/export" -b /tmp/emp.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF")
check "Export dashboard" "403" "$RESP"

echo -e "\n[5] Admin GET /api/dashboards → 200 (authorized)"
RESP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/dashboards" -b /tmp/admin.txt)
check "Admin list dashboards" "200" "$RESP"

echo -e "\n============================================="
echo "  RBAC TEST COMPLETE"
echo "============================================="
exit $FAIL
