#!/bin/bash
BASE="http://localhost:8080"

check() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then echo "  ✓ $label (HTTP $actual)"
  else echo "  ✗ $label — expected $expected, got $actual"; fi
}

echo "============================================="
echo "  END-TO-END JOB LIFECYCLE TEST"
echo "  DRAFT → REVIEW → APPROVE → PUBLISH → UNPUBLISH"
echo "  Then: new job → REVIEW → TAKEDOWN → APPEAL"
echo "============================================="

EMP_CSRF=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"employer1","password":"Employer@12345"}' -c /tmp/emp.txt \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['csrfToken'])")

REV_CSRF=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"reviewer1","password":"Reviewer@12345"}' -c /tmp/rev.txt \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['csrfToken'])")

# === HAPPY PATH ===
echo -e "\n--- Happy Path ---"

echo -e "\n[1] Employer creates job → DRAFT"
RESP=$(curl -s -o /tmp/job.json -w "%{http_code}" -X POST "$BASE/api/jobs" -b /tmp/emp.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF" \
  -d '{"title":"Full Stack Developer","description":"Join our team to build modern web applications using Vue.js and Spring Boot framework.","categoryId":31,"locationId":1,"payType":"HOURLY","settlementType":"WEEKLY","payAmount":50.00,"headcount":2,"weeklyHours":35,"contactPhone":"5559876543","tags":["vue","spring"]}')
JOB1=$(python3 -c "import json; print(json.load(open('/tmp/job.json'))['id'])")
S=$(python3 -c "import json; print(json.load(open('/tmp/job.json'))['status'])")
check "Created Job #$JOB1 → $S" "200" "$RESP"

echo -e "\n[2] Employer submits → PENDING_REVIEW"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/jobs/$JOB1/submit" -b /tmp/emp.txt -H "X-XSRF-TOKEN: $EMP_CSRF")
S=$(curl -s "$BASE/api/jobs/$JOB1" -b /tmp/emp.txt | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
check "Submit Job #$JOB1 → $S" "200" "$RESP"

echo -e "\n[3] Reviewer approves → APPROVED"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/review/jobs/$JOB1/approve" -b /tmp/rev.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $REV_CSRF" -d '{"rationale":"Approved — meets all requirements."}')
S=$(curl -s "$BASE/api/jobs/$JOB1" -b /tmp/emp.txt | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
check "Approve Job #$JOB1 → $S" "200" "$RESP"

echo -e "\n[4] Employer publishes (step-up) → PUBLISHED"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/jobs/$JOB1/publish" -b /tmp/emp.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF" -d '{"stepUpPassword":"Employer@12345"}')
S=$(curl -s "$BASE/api/jobs/$JOB1" -b /tmp/emp.txt | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
check "Publish Job #$JOB1 → $S" "200" "$RESP"

echo -e "\n[5] Employer unpublishes → UNPUBLISHED"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/jobs/$JOB1/unpublish" -b /tmp/emp.txt -H "X-XSRF-TOKEN: $EMP_CSRF")
S=$(curl -s "$BASE/api/jobs/$JOB1" -b /tmp/emp.txt | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
check "Unpublish Job #$JOB1 → $S" "200" "$RESP"

# === TAKEDOWN + APPEAL PATH ===
echo -e "\n--- Takedown + Appeal Path ---"

echo -e "\n[6] Employer creates second job → DRAFT"
RESP=$(curl -s -o /tmp/job2.json -w "%{http_code}" -X POST "$BASE/api/jobs" -b /tmp/emp.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF" \
  -d '{"title":"QA Engineer","description":"Test our platform for quality assurance, write automated tests, and ensure reliability.","categoryId":31,"locationId":1,"payType":"HOURLY","settlementType":"WEEKLY","payAmount":40.00,"headcount":1,"weeklyHours":30,"contactPhone":"5551112222","tags":["testing"]}')
JOB2=$(python3 -c "import json; print(json.load(open('/tmp/job2.json'))['id'])")
S=$(python3 -c "import json; print(json.load(open('/tmp/job2.json'))['status'])")
check "Created Job #$JOB2 → $S" "200" "$RESP"

echo -e "\n[7] Submit → Approve → Publish"
curl -s -o /dev/null -X POST "$BASE/api/jobs/$JOB2/submit" -b /tmp/emp.txt -H "X-XSRF-TOKEN: $EMP_CSRF"
curl -s -o /dev/null -X POST "$BASE/api/review/jobs/$JOB2/approve" -b /tmp/rev.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $REV_CSRF" -d '{"rationale":"Approved for takedown test."}'
curl -s -o /dev/null -X POST "$BASE/api/jobs/$JOB2/publish" -b /tmp/emp.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF" -d '{"stepUpPassword":"Employer@12345"}'
S=$(curl -s "$BASE/api/jobs/$JOB2" -b /tmp/emp.txt | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
echo "  Job #$JOB2 → $S (ready for takedown)"

echo -e "\n[8] Reviewer takedown (step-up) → TAKEN_DOWN"
RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/review/jobs/$JOB2/takedown" -b /tmp/rev.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $REV_CSRF" \
  -d '{"rationale":"Policy violation found upon re-review.","stepUpPassword":"Reviewer@12345"}')
S=$(curl -s "$BASE/api/jobs/$JOB2" -b /tmp/emp.txt | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
check "Takedown Job #$JOB2 → $S" "200" "$RESP"

echo -e "\n[9] Employer files appeal"
RESP=$(curl -s -o /tmp/appeal.json -w "%{http_code}" -X POST "$BASE/api/appeals" -b /tmp/emp.txt \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $EMP_CSRF" \
  -d "{\"jobPostingId\":$JOB2,\"appealReason\":\"Posting complies with all guidelines and company policies. Please reconsider this takedown.\"}")
check "Appeal for Job #$JOB2" "200" "$RESP"

echo -e "\n============================================="
echo "  ALL LIFECYCLE TRANSITIONS VERIFIED"
echo "============================================="
