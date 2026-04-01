#!/bin/bash
BASE="http://localhost:8080"

check() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then echo "  ✓ $label (HTTP $actual)"
  else echo "  ✗ $label — expected $expected, got $actual"; fi
}

echo "============================================="
echo "  RBAC TEST: EMPLOYER BLOCKED FROM DASHBOARDS"
echo "============================================="

# Login as employer
EMP_CSRF=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"employer1","password":"Employer@12345"}' -c /tmp/emp.txt \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['csrfToken'])")

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

# Login as admin to verify access works for authorized role
ADMIN_CSRF=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456789"}' -c /tmp/admin.txt \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['csrfToken'])")

echo -e "\n[5] Admin GET /api/dashboards → 200 (authorized)"
RESP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/dashboards" -b /tmp/admin.txt)
check "Admin list dashboards" "200" "$RESP"

echo -e "\n============================================="
echo "  RBAC TEST COMPLETE"
echo "============================================="
