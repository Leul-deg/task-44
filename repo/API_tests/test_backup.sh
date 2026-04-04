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
echo "  BACKUP/RESTORE RUNTIME TEST"
echo "============================================="

ADMIN_CSRF=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "$(admin_login_json)" -c /tmp/admin.txt \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('csrfToken',''))" 2>/dev/null)

if [ -z "$ADMIN_CSRF" ]; then echo "FATAL: admin login failed"; exit 1; fi

echo -e "\n[1] List existing backups (admin)"
RESP=$(curl -s -o /tmp/backups_list.json -w "%{http_code}" "$BASE/api/admin/backup/list" -b /tmp/admin.txt)
COUNT=$(python3 -c "import json; print(len(json.load(open('/tmp/backups_list.json'))))" 2>/dev/null || echo "?")
check "List backups ($COUNT found)" "200" "$RESP"

echo -e "\n[2] Trigger backup (admin, step-up)"
RESP=$(curl -s -o /tmp/backup.json -w "%{http_code}" -X POST "$BASE/api/admin/backup/trigger" \
  -b /tmp/admin.txt -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -d "{\"stepUpPassword\":\"$JOBOPS_ADMIN_PASSWORD\"}")
echo "  Response: $(cat /tmp/backup.json)"
check "Trigger backup" "200" "$RESP"

echo -e "\n[3] Verify backup count increased"
RESP=$(curl -s -o /tmp/backups_list2.json -w "%{http_code}" "$BASE/api/admin/backup/list" -b /tmp/admin.txt)
COUNT2=$(python3 -c "import json; print(len(json.load(open('/tmp/backups_list2.json'))))" 2>/dev/null || echo "?")
echo "  Backups before: $COUNT, after: $COUNT2"
if [ "$COUNT2" -gt "$COUNT" ] 2>/dev/null; then echo "  ✓ Backup count increased"; else echo "  ✗ Backup count did not increase"; fi

echo -e "\n[4] Employer cannot list backups → 403"
EMPLOYER_USER="backup_emp_$(date +%s)"
curl -s -o /dev/null -b /tmp/admin.txt -X POST "$BASE/api/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $ADMIN_CSRF" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"email\":\"$EMPLOYER_USER@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}"
EMP_CSRF=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"password\":\"StrongPass123!\"}" -c /tmp/emp.txt \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('csrfToken',''))" 2>/dev/null)
RESP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/admin/backup/list" -b /tmp/emp.txt)
check "Employer list backups blocked" "403" "$RESP"

echo -e "\n[5] Employer cannot trigger backup → 403"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/admin/backup/trigger" \
  -b /tmp/emp.txt -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF" \
  -d '{"stepUpPassword":"StrongPass123!"}')
check "Employer trigger backup blocked" "403" "$RESP"

echo -e "\n============================================="
echo "  BACKUP TEST COMPLETE"
echo "============================================="
exit $FAIL
