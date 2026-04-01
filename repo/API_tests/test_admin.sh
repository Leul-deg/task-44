#!/bin/bash
BASE=http://localhost:8080/api
PASS=0; FAIL=0
COOKIE=/tmp/admin_cookie.txt

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
  -d '{"username":"admin","password":"Admin@123456789"}')
CSRF=$(echo "$ADMIN_OUT" | jp csrfToken)

USERS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIE" -X GET "$BASE/admin/users")
check "List users returns 200" "200" "$USERS_CODE"

USERNAME="adm_test_$(date +%s)"
CREATE_OUTPUT=$(curl -s -o /tmp/admin_create.json -w "%{http_code}" -b "$COOKIE" -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$USERNAME@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}")
USER_ID=$(python3 -c "import json; print(json.load(open('/tmp/admin_create.json')).get('id',''))" 2>/dev/null)
check "Create admin user returns 200" "200" "$CREATE_OUTPUT"

ROLE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIE" -X PUT "$BASE/admin/users/$USER_ID/role" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"role":"REVIEWER","stepUpPassword":"Admin@123456789"}')
check "Change role to REVIEWER returns 200" "200" "$ROLE_CODE"

AUDIT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIE" -X GET "$BASE/admin/audit-logs")
check "List audit logs returns 200" "200" "$AUDIT_CODE"

BACKUP_TRIGGER_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIE" -X POST "$BASE/admin/backup/trigger" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"stepUpPassword":"Admin@123456789"}')
check "Trigger backup returns 200" "200" "$BACKUP_TRIGGER_CODE"

BACKUP_LIST_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$COOKIE" -X GET "$BASE/admin/backup/list")
check "List backups returns 200" "200" "$BACKUP_LIST_CODE"

echo ""
echo "Results: $PASS passed, $FAIL failed"
rm -f "$COOKIE" /tmp/admin_create.json
exit $FAIL
