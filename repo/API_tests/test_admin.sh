#!/bin/bash
BASE=http://localhost:8080/api
PASS=0; FAIL=0
COOKIE=/tmp/admin_cookie.txt
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

ADMIN_OUT=$(curl -s -c "$COOKIE" \
  -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "$(admin_login_json)")
CSRF=$(echo "$ADMIN_OUT" | jp csrfToken)

USERS_CODE=$(curl -s -o /tmp/admin_users.json -w "%{http_code}" -b "$COOKIE" -X GET "$BASE/admin/users")
check "List users returns 200" "200" "$USERS_CODE"
grep -qE '"totalElements"|"items"' /tmp/admin_users.json || { echo "FAIL: list users response missing totalElements or items"; FAIL=$((FAIL+1)); }

USERNAME="adm_test_$(date +%s)"
CREATE_OUTPUT=$(curl -s -o /tmp/admin_create.json -w "%{http_code}" -b "$COOKIE" -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$USERNAME@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}")
USER_ID=$(python3 -c "import json; print(json.load(open('/tmp/admin_create.json')).get('id',''))" 2>/dev/null)
check "Create admin user returns 200" "200" "$CREATE_OUTPUT"
grep -q '"id"' /tmp/admin_create.json || { echo "FAIL: create user response missing id"; FAIL=$((FAIL+1)); }
grep -q "\"$USERNAME\"" /tmp/admin_create.json || { echo "FAIL: create user response missing username"; FAIL=$((FAIL+1)); }

ROLE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIE" -X PUT "$BASE/admin/users/$USER_ID/role" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d "{\"role\":\"REVIEWER\",\"stepUpPassword\":\"$JOBOPS_ADMIN_PASSWORD\"}")
check "Change role to REVIEWER returns 200" "200" "$ROLE_CODE"

AUDIT_CODE=$(curl -s -o /tmp/admin_audit.json -w "%{http_code}" -b "$COOKIE" -X GET "$BASE/admin/audit-logs")
check "List audit logs returns 200" "200" "$AUDIT_CODE"
grep -qE '"totalElements"|"items"' /tmp/admin_audit.json || { echo "FAIL: audit logs response missing totalElements or items"; FAIL=$((FAIL+1)); }

BACKUP_TRIGGER_CODE=$(curl -s -o /tmp/admin_backup_trigger.json -w "%{http_code}" -b "$COOKIE" -X POST "$BASE/admin/backup/trigger" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d "{\"stepUpPassword\":\"$JOBOPS_ADMIN_PASSWORD\"}")
check "Trigger backup returns 200" "200" "$BACKUP_TRIGGER_CODE"
grep -q '"id"' /tmp/admin_backup_trigger.json || { echo "FAIL: trigger backup response missing id"; FAIL=$((FAIL+1)); }
grep -q '"status"' /tmp/admin_backup_trigger.json || { echo "FAIL: trigger backup response missing status"; FAIL=$((FAIL+1)); }
grep -q '"filename"' /tmp/admin_backup_trigger.json || { echo "FAIL: trigger backup response missing filename"; FAIL=$((FAIL+1)); }

BACKUP_LIST_CODE=$(curl -s -o /tmp/admin_backup_list.json -w "%{http_code}" -b "$COOKIE" -X GET "$BASE/admin/backup/list")
check "List backups returns 200" "200" "$BACKUP_LIST_CODE"
grep -q '^\[' /tmp/admin_backup_list.json || head -c1 /tmp/admin_backup_list.json | grep -q '\[' || { echo "FAIL: list backups response is not a JSON array"; FAIL=$((FAIL+1)); }

echo ""
echo "Results: $PASS passed, $FAIL failed"
rm -f "$COOKIE" /tmp/admin_create.json /tmp/admin_users.json /tmp/admin_audit.json /tmp/admin_backup_trigger.json /tmp/admin_backup_list.json
exit $FAIL
