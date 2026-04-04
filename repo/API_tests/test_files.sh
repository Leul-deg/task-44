#!/bin/bash
BASE=http://localhost:8080/api
PASS=0; FAIL=0
ADMIN_COOKIE=/tmp/files_admin.cookie
EMPLOYER_COOKIE=/tmp/files_emp.cookie
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

get_or_create_category() {
  local name="$1" cookie="$2" csrf="$3"
  local id
  curl -s -o /tmp/fc_cat.json -b "$cookie" -X POST "$BASE/admin/categories" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $csrf" \
    -d "{\"name\":\"$name\"}" >/dev/null
  id=$(python3 -c "import json; print(json.load(open('/tmp/fc_cat.json')).get('id',''))" 2>/dev/null)
  if [ -z "$id" ] || [ "$id" = "" ]; then
    id=$(curl -s -b "$cookie" "$BASE/categories" | python3 -c "import sys,json; cats=json.load(sys.stdin); print(next((c['id'] for c in cats if c['name']=='$name'), cats[0]['id'] if cats else ''))" 2>/dev/null)
  fi
  echo "$id"
}

get_or_create_location() {
  local state="$1" city="$2" cookie="$3" csrf="$4"
  local id
  curl -s -o /tmp/fc_loc.json -b "$cookie" -X POST "$BASE/admin/locations" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $csrf" \
    -d "{\"state\":\"$state\",\"city\":\"$city\"}" >/dev/null
  id=$(python3 -c "import json; print(json.load(open('/tmp/fc_loc.json')).get('id',''))" 2>/dev/null)
  if [ -z "$id" ] || [ "$id" = "" ]; then
    id=$(curl -s -b "$cookie" "$BASE/locations" | python3 -c "import sys,json; locs=json.load(sys.stdin); print(next((l['id'] for l in locs if l['city']=='$city'), locs[0]['id'] if locs else ''))" 2>/dev/null)
  fi
  echo "$id"
}

# Admin login
ADMIN_OUT=$(curl -s -c "$ADMIN_COOKIE" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" -d "$(admin_login_json)")
CSRF_ADMIN=$(echo "$ADMIN_OUT" | jp csrfToken)

CATEGORY_ID=$(get_or_create_category "FileTestCat" "$ADMIN_COOKIE" "$CSRF_ADMIN")
echo "Using category ID: $CATEGORY_ID"
LOCATION_ID=$(get_or_create_location "TX" "Austin" "$ADMIN_COOKIE" "$CSRF_ADMIN")
echo "Using location ID: $LOCATION_ID"

# Create employer via admin
EMPLOYER_USER="files_emp_$(date +%s)"
curl -s -o /dev/null -b "$ADMIN_COOKIE" -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_ADMIN" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"email\":\"$EMPLOYER_USER@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}"

# Employer login
EMP_OUT=$(curl -s -c "$EMPLOYER_COOKIE" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"password\":\"StrongPass123!\"}")
CSRF_EMP=$(echo "$EMP_OUT" | jp csrfToken)

# Create job for claim
CREATE_CODE=$(curl -s -o /tmp/fc_job.json -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X POST "$BASE/jobs" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"title\":\"File Test Job\",\"description\":\"Job used to create a claim for file upload testing in automated tests.\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"payType\":\"HOURLY\",\"settlementType\":\"WEEKLY\",\"payAmount\":30.00,\"headcount\":3,\"weeklyHours\":30,\"contactPhone\":\"5559870000\",\"tags\":[\"files\"]}")
JOB_ID=$(python3 -c "import json; print(json.load(open('/tmp/fc_job.json')).get('id',''))" 2>/dev/null)
check "Create job for claim" "200" "$CREATE_CODE"

# Create claim
CLAIM_CODE=$(curl -s -o /tmp/fc_claim.json -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X POST "$BASE/claims" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"jobPostingId\":$JOB_ID,\"description\":\"Files test claim for upload verification\"}")
CLAIM_ID=$(python3 -c "import json; print(json.load(open('/tmp/fc_claim.json')).get('id',''))" 2>/dev/null)
check "Create claim" "200" "$CLAIM_CODE"

# Upload valid PDF
printf '%%PDF-1.4 test content here' > /tmp/test_upload.pdf
UPLOAD_CODE=$(curl -s -o /tmp/fc_upload.json -w "%{http_code}" -b "$EMPLOYER_COOKIE" \
  -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -F "file=@/tmp/test_upload.pdf" -F "entityType=CLAIM" -F "entityId=$CLAIM_ID" \
  "$BASE/files/upload")
FILE_ID=$(python3 -c "import json; print(json.load(open('/tmp/fc_upload.json')).get('id',''))" 2>/dev/null)
check "Upload valid PDF returns 200" "200" "$UPLOAD_CODE"

# Oversized file (>10MB)
dd if=/dev/zero of=/tmp/bigfile.pdf bs=1M count=11 2>/dev/null
BIG_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" \
  -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -F "file=@/tmp/bigfile.pdf" -F "entityType=CLAIM" -F "entityId=$CLAIM_ID" \
  "$BASE/files/upload")
if [ "$BIG_CODE" = "400" ] || [ "$BIG_CODE" = "413" ] || [ "$BIG_CODE" = "500" ]; then
  echo "PASS: Oversized file rejected (HTTP $BIG_CODE)"; PASS=$((PASS+1))
else
  echo "FAIL: Oversized file not rejected (expected 400/413/500, got $BIG_CODE)"; FAIL=$((FAIL+1))
fi

# Wrong file type
printf 'MZ' > /tmp/test.exe
EXE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" \
  -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -F "file=@/tmp/test.exe" -F "entityType=CLAIM" -F "entityId=$CLAIM_ID" \
  "$BASE/files/upload")
check "Wrong type returns 400" "400" "$EXE_CODE"

# Download
DOWNLOAD_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" "$BASE/files/$FILE_ID/download")
check "Download file returns 200" "200" "$DOWNLOAD_CODE"

echo ""
echo "Results: $PASS passed, $FAIL failed"
rm -f /tmp/test_upload.pdf /tmp/bigfile.pdf /tmp/test.exe /tmp/fc_*.json "$ADMIN_COOKIE" "$EMPLOYER_COOKIE"
exit $FAIL
