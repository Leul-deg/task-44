#!/bin/bash
BASE=http://localhost:8080/api
PASS=0; FAIL=0
ADMIN_COOKIE=/tmp/review_admin.cookie
EMPLOYER_COOKIE=/tmp/review_emp.cookie
REVIEWER_COOKIE=/tmp/review_rev.cookie

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
  curl -s -o /tmp/rv_cat.json -b "$cookie" -X POST "$BASE/admin/categories" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $csrf" \
    -d "{\"name\":\"$name\"}" >/dev/null
  id=$(python3 -c "import json; print(json.load(open('/tmp/rv_cat.json')).get('id',''))" 2>/dev/null)
  if [ -z "$id" ] || [ "$id" = "" ]; then
    id=$(curl -s -b "$cookie" "$BASE/categories" | python3 -c "import sys,json; cats=json.load(sys.stdin); print(next((c['id'] for c in cats if c['name']=='$name'), cats[0]['id'] if cats else ''))" 2>/dev/null)
  fi
  echo "$id"
}

get_or_create_location() {
  local state="$1" city="$2" cookie="$3" csrf="$4"
  local id
  curl -s -o /tmp/rv_loc.json -b "$cookie" -X POST "$BASE/admin/locations" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $csrf" \
    -d "{\"state\":\"$state\",\"city\":\"$city\"}" >/dev/null
  id=$(python3 -c "import json; print(json.load(open('/tmp/rv_loc.json')).get('id',''))" 2>/dev/null)
  if [ -z "$id" ] || [ "$id" = "" ]; then
    id=$(curl -s -b "$cookie" "$BASE/locations" | python3 -c "import sys,json; locs=json.load(sys.stdin); print(next((l['id'] for l in locs if l['city']=='$city'), locs[0]['id'] if locs else ''))" 2>/dev/null)
  fi
  echo "$id"
}

# Admin login
ADMIN_OUT=$(curl -s -c "$ADMIN_COOKIE" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"Admin@123456789"}')
CSRF_ADMIN=$(echo "$ADMIN_OUT" | jp csrfToken)

CATEGORY_ID=$(get_or_create_category "ReviewCat" "$ADMIN_COOKIE" "$CSRF_ADMIN")
echo "Using category ID: $CATEGORY_ID"
LOCATION_ID=$(get_or_create_location "NY" "New York" "$ADMIN_COOKIE" "$CSRF_ADMIN")
echo "Using location ID: $LOCATION_ID"

# Create employer and reviewer via admin
EMPLOYER_USER="review_emp_$(date +%s)"
REVIEWER_USER="review_rev_$(date +%s)"

curl -s -o /dev/null -b "$ADMIN_COOKIE" -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_ADMIN" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"email\":\"$EMPLOYER_USER@test.com\",\"password\":\"StrongPass123!\",\"role\":\"EMPLOYER\"}"

curl -s -o /dev/null -b "$ADMIN_COOKIE" -X POST "$BASE/admin/users" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_ADMIN" \
  -d "{\"username\":\"$REVIEWER_USER\",\"email\":\"$REVIEWER_USER@test.com\",\"password\":\"StrongPass123!\",\"role\":\"REVIEWER\"}"

# Employer login
EMP_OUT=$(curl -s -c "$EMPLOYER_COOKIE" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$EMPLOYER_USER\",\"password\":\"StrongPass123!\"}")
CSRF_EMP=$(echo "$EMP_OUT" | jp csrfToken)

# Reviewer login
REV_OUT=$(curl -s -c "$REVIEWER_COOKIE" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$REVIEWER_USER\",\"password\":\"StrongPass123!\"}")
CSRF_REV=$(echo "$REV_OUT" | jp csrfToken)

# Create job
CREATE_CODE=$(curl -s -o /tmp/rv_job.json -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X POST "$BASE/jobs" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"title\":\"Review Flow Job\",\"description\":\"This is a review job posting used in automated tests for review workflow.\",\"categoryId\":$CATEGORY_ID,\"locationId\":$LOCATION_ID,\"payType\":\"HOURLY\",\"settlementType\":\"WEEKLY\",\"payAmount\":35.00,\"headcount\":4,\"weeklyHours\":30,\"contactPhone\":\"5559876543\",\"tags\":[\"review\"]}")
JOB_ID=$(python3 -c "import json; print(json.load(open('/tmp/rv_job.json')).get('id',''))" 2>/dev/null)
check "Create job for review" "200" "$CREATE_CODE"

# Submit
SUBMIT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" \
  -X POST "$BASE/jobs/$JOB_ID/submit" -H "X-XSRF-TOKEN: $CSRF_EMP")
check "Submit job for review" "200" "$SUBMIT_CODE"

# Approve
APPROVE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$REVIEWER_COOKIE" -X POST "$BASE/review/jobs/$JOB_ID/approve" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_REV" \
  -d '{"rationale":"Looks good, approved for posting"}')
check "Reviewer approves job" "200" "$APPROVE_CODE"

# Publish
PUBLISH_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X POST "$BASE/jobs/$JOB_ID/publish" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d '{"stepUpPassword":"StrongPass123!"}')
check "Employer publishes job" "200" "$PUBLISH_CODE"

# Takedown
TAKEDOWN_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$REVIEWER_COOKIE" -X POST "$BASE/review/jobs/$JOB_ID/takedown" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_REV" \
  -d '{"rationale":"Policy violation found in posting","stepUpPassword":"StrongPass123!"}')
check "Reviewer takedowns job" "200" "$TAKEDOWN_CODE"

# Appeal
APPEAL_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X POST "$BASE/appeals" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_EMP" \
  -d "{\"jobPostingId\":$JOB_ID,\"appealReason\":\"The posting complies with all policies and should be reinstated\"}")
check "Employer creates appeal" "200" "$APPEAL_CODE"

# List appeals and get first ID for this job
APPEAL_ID=$(curl -s -b "$REVIEWER_COOKIE" -X GET "$BASE/appeals" \
  | python3 -c "
import sys,json
data=json.load(sys.stdin)
items = data.get('items', data) if isinstance(data, dict) else data
print(items[0]['id'] if items else '')
" 2>/dev/null)

# Process appeal
PROCESS_CODE=$(curl -s -o /dev/null -w "%{http_code}" -b "$REVIEWER_COOKIE" -X POST "$BASE/appeals/$APPEAL_ID/process" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $CSRF_REV" \
  -d '{"decision":"GRANTED","reviewerRationale":"Appeal is valid, reinstating the posting"}')
check "Reviewer processes appeal" "200" "$PROCESS_CODE"

# Verify final status
FINAL_CODE=$(curl -s -o /tmp/rv_final.json -w "%{http_code}" -b "$EMPLOYER_COOKIE" -X GET "$BASE/jobs/$JOB_ID")
FINAL_STATUS=$(python3 -c "import json; print(json.load(open('/tmp/rv_final.json')).get('status',''))" 2>/dev/null)
check "Final job GET returns 200" "200" "$FINAL_CODE"
echo "  Final status: $FINAL_STATUS"

echo ""
echo "Results: $PASS passed, $FAIL failed"
rm -f "$ADMIN_COOKIE" "$EMPLOYER_COOKIE" "$REVIEWER_COOKIE" /tmp/rv_*.json
exit $FAIL
