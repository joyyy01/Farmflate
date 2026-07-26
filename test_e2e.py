"""Farmflate API E2E test script — runs against live servers."""
import json, sys, time, urllib.request, urllib.error

BASE = "http://localhost:8080/api"
PYTHON_BASE = "http://127.0.0.1:8000"
TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI0OTg3a2tAbmF2ZXIuY29tIiwiaWF0IjoxNzg1MDQ1OTgwLCJleHAiOjE3ODUxMzIzODB9.E6ileXxxVVLJUBumrsMi4x3SLjuUxBU4TKbtc5m3MD8"
INTERNAL_KEY = "farmflate-local-internal-key"

passed = 0
failed = 0

def req(method, url, body=None, headers=None, expect_status=None):
    global passed, failed
    hdrs = headers or {}
    data = json.dumps(body).encode() if body else None
    if body and "Content-Type" not in hdrs:
        hdrs["Content-Type"] = "application/json"
    r = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        resp = urllib.request.urlopen(r, timeout=30)
        code = resp.status
        raw = resp.read().decode()
    except urllib.error.HTTPError as e:
        code = e.code
        raw = e.read().decode()
    except Exception as e:
        print(f"  FAIL  {method} {url} -> {e}")
        failed += 1
        return None, 0
    ok = True
    if expect_status and code != expect_status:
        ok = False
    tag = "PASS" if ok else "FAIL"
    if ok: passed += 1
    else: failed += 1
    try:
        parsed = json.loads(raw) if raw else None
    except json.JSONDecodeError:
        parsed = raw
    summary = json.dumps(parsed, ensure_ascii=False)[:200] if parsed else "(empty)"
    print(f"  {tag}  {method} {url.replace(BASE,'').replace(PYTHON_BASE,'')} -> {code}  {summary}")
    return parsed, code

def auth_hdrs():
    return {"Authorization": f"Bearer {TOKEN}"}

print("=" * 60)
print("1. AUTH & BASIC ENDPOINTS")
print("=" * 60)
req("GET", f"{BASE}/health", expect_status=200)
req("GET", f"{BASE}/home", headers=auth_hdrs(), expect_status=200)
req("GET", f"{BASE}/home", expect_status=401)  # no auth
req("GET", f"{BASE}/home", headers={"Authorization": "Bearer bad"}, expect_status=401)

print("\n" + "=" * 60)
print("2. REGION LIST")
print("=" * 60)
sidos, _ = req("GET", f"{BASE}/regions/sidos", headers=auth_hdrs(), expect_status=200)
if sidos and len(sidos) > 0:
    sido_code = sidos[0]["sidoCode"]
    print(f"  INFO  First sido: {sidos[0]['sidoName']} ({sido_code}), total={len(sidos)}")
    sigungus, _ = req("GET", f"{BASE}/regions/sidos/{sido_code}/sigungus", headers=auth_hdrs(), expect_status=200)
    if sigungus and len(sigungus) > 0:
        print(f"  INFO  First sigungu: {sigungus[0]['sigunguName']} ({sigungus[0]['sigunguCode']}), total={len(sigungus)}")
    # Edge: invalid sido code
    req("GET", f"{BASE}/regions/sidos/INVALID/sigungus", headers=auth_hdrs())
else:
    print("  FAIL  No sidos returned!")
    failed += 1

print("\n" + "=" * 60)
print("3. REGION ANALYSIS E2E")
print("=" * 60)
# Use a known valid region: 전북 고창군 (52180 / 52790)
analysis_body = {
    "sidoCode": "52",
    "sidoName": "전북특별자치도",
    "sigunguCode": "52790",
    "sigunguName": "고창군",
    "idempotencyKey": f"e2e-test-{int(time.time())}"
}
status, code = req("POST", f"{BASE}/regions/analysis", body=analysis_body, headers=auth_hdrs())
if status and code in (200, 201):
    analysis_id = status.get("analysisId")
    print(f"  INFO  analysisId={analysis_id}, status={status.get('status')}")

    # Poll until terminal
    terminal = False
    for i in range(30):
        time.sleep(1)
        st, _ = req("GET", f"{BASE}/regions/analysis/{analysis_id}/status", headers=auth_hdrs(), expect_status=200)
        if st:
            s = (st.get("status") or "").upper()
            step = st.get("currentStepCode") or st.get("currentStep") or ""
            codes = st.get("completedStepCodes") or []
            print(f"  POLL  [{i+1}] status={s} step={step} completed={codes}")
            if s in ("COMPLETED", "PARTIAL", "FAILED"):
                terminal = True
                if s == "FAILED":
                    print(f"  FAIL  Analysis FAILED: {st.get('errorCode')} {st.get('errorMessage')}")
                    failed += 1
                break
    if not terminal:
        print("  FAIL  Analysis did not complete in 30s")
        failed += 1

    # Get report
    if terminal and s != "FAILED":
        report, rc = req("GET", f"{BASE}/regions/reports/{analysis_id}", headers=auth_hdrs(), expect_status=200)
        if report:
            # Verify analysisId matches
            if report.get("analysisId") == analysis_id:
                print(f"  PASS  report.analysisId matches entity ID")
                passed += 1
            else:
                print(f"  FAIL  report.analysisId={report.get('analysisId')} != entity={analysis_id}")
                failed += 1
            # Verify cropResults
            crops = report.get("cropResults") or []
            recs = report.get("recommendedCrops") or []
            print(f"  INFO  cropResults={len(crops)}, recommendedCrops={len(recs)}")
            for c in crops:
                print(f"    crop: {c.get('cropName')} code={c.get('cropCode')} score={c.get('score')} calculable={c.get('calculable')}")
            # Verify components
            comp = report.get("components") or {}
            print(f"  INFO  components: climate={comp.get('climate',{}).get('score')}, soil={comp.get('soil',{}).get('score')}")
            # Verify regionScore
            print(f"  INFO  regionScore={report.get('regionScore')}, grade={report.get('grade')}")

            # Save for later tests
            with open("_test_report.json", "w", encoding="utf-8") as f:
                json.dump({"analysisId": analysis_id, "report": report}, f, ensure_ascii=False)
else:
    print(f"  FAIL  Could not create analysis (code={code})")
    failed += 1

print("\n" + "=" * 60)
print("4. CROP PREVIEW + FIELD CREATE + DASHBOARD")
print("=" * 60)
try:
    with open("_test_report.json", "r", encoding="utf-8") as f:
        saved = json.load(f)
    analysis_id = saved["analysisId"]
    report = saved["report"]
    crops = report.get("cropResults") or []
    calculable = [c for c in crops if c.get("calculable") is not False and c.get("cropCode")]
    if calculable:
        crop = calculable[0]
        preview_body = {
            "fieldName": "E2E 테스트 밭",
            "cropCode": crop["cropCode"],
            "cropName": crop["cropName"],
            "cultivationMethod": "outdoor",
            "cultivationStartDate": "2026-07-01",
            "stage": "before",
            "regionAnalysisId": analysis_id
        }
        preview, pc = req("POST", f"{BASE}/fields/preview", body=preview_body, headers=auth_hdrs(), expect_status=200)
        if preview:
            suit = preview.get("suitabilityReport") or {}
            print(f"  INFO  preview score={suit.get('suitabilityScore')}, grade={suit.get('grade')}")
            print(f"  INFO  preview has id field: {'id' in preview}")
            if "id" in preview and preview["id"] is not None:
                print(f"  FAIL  Preview should NOT have an id field!")
                failed += 1
            else:
                print(f"  PASS  Preview has no id (correct)")
                passed += 1

        # Create field
        field, fc = req("POST", f"{BASE}/fields", body=preview_body, headers=auth_hdrs(), expect_status=201)
        if field:
            field_id = field.get("id")
            print(f"  INFO  Created field id={field_id}")

            # Get field dashboard
            dash, dc = req("GET", f"{BASE}/fields/{field_id}/dashboard", headers=auth_hdrs(), expect_status=200)
            if dash:
                rpt = dash.get("report") or {}
                print(f"  INFO  dashboard status={rpt.get('status')}, score={rpt.get('statusScore')}, zone={rpt.get('statusScoreZone')}")
                print(f"  INFO  headline={rpt.get('headline')}")
                tasks = dash.get("tasks") or []
                alerts = dash.get("alerts") or []
                history = dash.get("history") or []
                print(f"  INFO  tasks={len(tasks)}, alerts={len(alerts)}, history={len(history)}")
                if history:
                    h0 = history[0]
                    print(f"  INFO  history[0]: date={h0.get('date')}, mgmt={h0.get('managementSummary')}, metric={h0.get('keyMetric')}")
                # Verify score direction (0=good, 100=bad)
                score = rpt.get("statusScore")
                zone = rpt.get("statusScoreZone")
                if score is not None:
                    if score <= 30 and zone == "적정":
                        print(f"  PASS  Score {score} -> zone '{zone}' (direction correct)")
                        passed += 1
                    elif score <= 65 and zone == "주의":
                        print(f"  PASS  Score {score} -> zone '{zone}' (direction correct)")
                        passed += 1
                    elif score > 65 and zone == "위험":
                        print(f"  PASS  Score {score} -> zone '{zone}' (direction correct)")
                        passed += 1
                    else:
                        print(f"  FAIL  Score {score} -> zone '{zone}' (direction mismatch!)")
                        failed += 1

            # Get fields list
            fields, flc = req("GET", f"{BASE}/fields", headers=auth_hdrs(), expect_status=200)
            if fields:
                print(f"  INFO  Total fields: {len(fields)}")

        # Edge: preview with invalid analysisId
        bad_preview = dict(preview_body, regionAnalysisId="00000000-0000-0000-0000-000000000000")
        req("POST", f"{BASE}/fields/preview", body=bad_preview, headers=auth_hdrs(), expect_status=404)

        # Edge: preview with non-calculable crop
        non_calc = [c for c in crops if c.get("calculable") is False]
        if non_calc:
            nc = non_calc[0]
            nc_body = dict(preview_body, cropCode=nc.get("cropCode",""), cropName=nc["cropName"])
            req("POST", f"{BASE}/fields/preview", body=nc_body, headers=auth_hdrs())
    else:
        print("  SKIP  No calculable crops in report")
except FileNotFoundError:
    print("  SKIP  No saved report (analysis may have failed)")

print("\n" + "=" * 60)
print("5. AI AGENT (Python)")
print("=" * 60)
agent_body = {
    "message": "감자 재배 시기 알려줘",
    "fact_package": {
        "requestId": "e2e-test-1",
        "question": "감자 재배 시기 알려줘"
    }
}
agent_hdrs = {"Content-Type": "application/json", "X-Farmflate-Internal-Key": INTERNAL_KEY}
result, ac = req("POST", f"{PYTHON_BASE}/api/v1/agent/run", body=agent_body, headers=agent_hdrs, expect_status=200)
if result:
    answer = result.get("answer") or {}
    print(f"  INFO  answer basisType={answer.get('basisType')}")
    print(f"  INFO  answer text={str(answer.get('answer',''))[:120]}")

# Edge: missing internal key
req("POST", f"{PYTHON_BASE}/api/v1/agent/run", body=agent_body,
    headers={"Content-Type": "application/json"}, expect_status=401)

# Edge: wrong internal key
req("POST", f"{PYTHON_BASE}/api/v1/agent/run", body=agent_body,
    headers={"Content-Type": "application/json", "X-Farmflate-Internal-Key": "wrong-key"}, expect_status=401)

# Chat endpoint
chat_body = {"message": "상추 물주기 어떻게 해?"}
chat_result, cc = req("POST", f"{PYTHON_BASE}/api/v1/chat", body=chat_body, headers=agent_hdrs, expect_status=200)
if chat_result:
    print(f"  INFO  chat answer={str(chat_result.get('answer',''))[:120]}")

print("\n" + "=" * 60)
print("6. EDGE CASES")
print("=" * 60)
# Invalid analysis ID for status
req("GET", f"{BASE}/regions/analysis/00000000-0000-0000-0000-000000000000/status", headers=auth_hdrs(), expect_status=404)
# Invalid analysis ID for report
req("GET", f"{BASE}/regions/reports/00000000-0000-0000-0000-000000000000", headers=auth_hdrs(), expect_status=404)
# Missing required fields in analysis
req("POST", f"{BASE}/regions/analysis", body={"sidoCode": "52"}, headers=auth_hdrs(), expect_status=400)
# Invalid field dashboard
req("GET", f"{BASE}/fields/99999/dashboard", headers=auth_hdrs())
# Community posts (public read)
req("GET", f"{BASE}/community/posts", headers=auth_hdrs(), expect_status=200)

print("\n" + "=" * 60)
print(f"RESULTS: {passed} passed, {failed} failed")
print("=" * 60)
sys.exit(1 if failed > 0 else 0)
