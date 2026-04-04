#!/bin/bash
BASE=http://localhost:8080/api
PASS=0; FAIL=0
ADMIN_COOKIE=/tmp/api_admin.cookie
EMPLOYER_COOKIE=/tmp/api_emp.cookie
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
  local code body id
  code=$(curl -s -o /tmp/jp_cat.json -w "%{http_code}" -b "$cookie" -X POST "$BASE/admin/categories" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $csrf" \
    -d "{\"name\":\"$name\"}")
  id=$(python3 -c "import json; print(json.load(open('/tmp/jp_cat.json')).get('id',''))" 2>/dev/null)
  if [ -z "$id" ] || [ "$id" = "" ]; then
    id=$(curl -s -b "$cookie" "$BASE/categories" | python3 -c "import sys,json; cats=json.load(sys.stdin); print(next((c['id'] for c in cats if c['name']=='$name'), cats[0]['id'] if cats else ''))" 2>/dev/null)
  fi
  echo "$id"
}

get_or_create_location() {
  local state="$1" city="$2" cookie="$3" csrf="$4"
  local code id
  code=$(curl -s -o /tmp/jp_loc.json -w "%{http_code}" -b "$cookie" -X POST "$BASE/admin/locations" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $csrf" \
    -d "{\"state\":\"$state\",\"city\":\"$city\"}")
  id=$(python3 -c "import json; print(json.load(open('/tmp/jp_loc.json')).get('id',''))" 2>/dev/null)
  if [ -z "$id" ] || [ "$id" = "" ]; then
    id=$(curl -s -b "$cookie" "$BASE/locations" | python3 -c "import sys,json; locs=json.load(sys.stdin); print(next((l['id'] for l in locs if l['city']=='$city'), locs[0]['id'] if locs else ''))" 2>/dev/null)
  fi
  echo "$id"
}

# Admin login
ADMIN_OUT=$(curl -s -c "$ADMIN_COOKIE" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" -d "$(admin_login_json)")
CSRF_ADMIN=$(echo "$ADMIN_OUT" | jp csrfToken)

CATEGORY_ID=$(get_or_create_category "JobTestCat" "$ADMIN_COOKIE" "$CSRF_ADMIN")
echo "Using category ID: $CATEGORY_ID"
LOCATION_ID=$(get_or_create_location "CA" "Los Angeles" "$ADMIN_COOKIE" "$CSRF_ADMIN")
echo "Using location ID: $LOCATION_ID"

# Create employer via admin
EMPLOYER_USER="job_emp_$(date +%s)"
curl -s -o /dev/null -b "$ADMIN_COOKIE" -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_ADMIN" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"email\":\"$EMPLOYER_USER@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}"

# Employer login
EMP_OUT=$(curl -s -c "$EMPLOYER_COOKIE" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"password\":\"StrongPass123!\"}")
CSRF_EMP=$(echo "$EMP_OUT" | jp csrfToken)

# Create job posting
CREATE_CODE=$(curl -s -o /tmp/jp_job.json -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X POST "$BASE/jobs" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"title\":\"Test Job Posting Title Here\",\"description\":\"This is a test job posting with enough description text to pass validation requirements.\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"payType\":\"HOURLY\",\"settlementType\":\"WEEKLY\",\"payAmount\":25.00,\"headcount\":5,\"weeklyHours\":20,\"contactPhone\":\"5551234567\",\"tags\":[\"test\"]}")
JOB_ID=$(python3 -c "import json; print(json.load(open('/tmp/jp_job.json')).get('id',''))" 2>/dev/null)
check "Create job returns 200" "200" "$CREATE_CODE"

# Get job
GET_CODE=$(curl -s -o /tmp/jp_get.json -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X GET "$BASE/jobs/$JOB_ID")
STATUS=$(python3 -c "import json; print(json.load(open('/tmp/jp_get.json')).get('status',''))" 2>/dev/null)
check "Get job returns 200" "200" "$GET_CODE"
check "Job starts as DRAFT" "DRAFT" "$STATUS"

# Edit draft
EDIT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X PUT "$BASE/jobs/$JOB_ID" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"title\":\"Updated Title\",\"description\":\"This is a test job posting with enough description text to pass validation requirements.\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"payType\":\"HOURLY\",\"settlementType\":\"WEEKLY\",\"payAmount\":25.00,\"headcount\":5,\"weeklyHours\":20,\"contactPhone\":\"5551234567\",\"tags\":[\"test\"]}")
check "Edit draft job returns 200" "200" "$EDIT_CODE"

# Submit for review
SUBMIT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" \
  -X POST "$BASE/jobs/$JOB_ID/submit" -H "X-XSRF-TOKEN: $CSRF_EMP")
check "Submit for review returns 200" "200" "$SUBMIT_CODE"

# Edit after submit should fail
POST_SUBMIT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X PUT "$BASE/jobs/$JOB_ID" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"title\":\"Bad Edit\",\"description\":\"This is a test job posting with enough description text to pass validation requirements.\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"payType\":\"HOURLY\",\"settlementType\":\"WEEKLY\",\"payAmount\":25.00,\"headcount\":5,\"weeklyHours\":20,\"contactPhone\":\"5551234567\",\"tags\":[\"test\"]}")
check "Edit after submit returns 400" "400" "$POST_SUBMIT_CODE"

# Hourly pay below $12
LOW_PAY_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X POST "$BASE/jobs" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"title\":\"Low Pay Job\",\"description\":\"This is a test job posting with enough description text to pass validation requirements.\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"payType\":\"HOURLY\",\"settlementType\":\"WEEKLY\",\"payAmount\":11.00,\"headcount\":5,\"weeklyHours\":20,\"contactPhone\":\"5551234567\",\"tags\":[\"test\"]}")
check "Hourly pay below 12 returns 400" "400" "$LOW_PAY_CODE"

# Headcount 0
ZERO_HC_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X POST "$BASE/jobs" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"title\":\"Zero Headcount\",\"description\":\"This is a test job posting with enough description text to pass validation requirements.\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"payType\":\"HOURLY\",\"settlementType\":\"WEEKLY\",\"payAmount\":25.00,\"headcount\":0,\"weeklyHours\":20,\"contactPhone\":\"5551234567\",\"tags\":[\"test\"]}")
check "Headcount 0 returns 400" "400" "$ZERO_HC_CODE"

echo ""
echo "Results: $PASS passed, $FAIL failed"
rm -f "$ADMIN_COOKIE" "$EMPLOYER_COOKIE" /tmp/jp_*.json
exit $FAIL
