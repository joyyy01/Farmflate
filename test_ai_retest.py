"""Re-test the 3 previously failed AI agent tests."""
import json, urllib.request, urllib.error

BASE = "http://127.0.0.1:8000"
KEY = "farmflate-local-internal-key"
passed = 0
failed = 0

def test(name, url, body, headers, expect):
    global passed, failed
    data = json.dumps(body).encode()
    r = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        resp = urllib.request.urlopen(r, timeout=15)
        code = resp.status
    except urllib.error.HTTPError as e:
        code = e.code
    ok = code == expect
    tag = "PASS" if ok else "FAIL"
    if ok: passed += 1
    else: failed += 1
    print(f"  {tag}  {name}: got {code}, expected {expect}")

hdrs_ok = {"Content-Type": "application/json", "X-Farmflate-Internal-Key": KEY}
hdrs_no = {"Content-Type": "application/json"}
hdrs_bad = {"Content-Type": "application/json", "X-Farmflate-Internal-Key": "wrong-key"}
agent_body = {"message": "test", "fact_package": {"requestId": "t1", "question": "test"}}
chat_body = {"message": "상추 물주기 어떻게 해?"}

print("AI Agent auth + routing re-test")
test("agent with valid key", f"{BASE}/api/v1/agent/run", agent_body, hdrs_ok, 200)
test("agent without key", f"{BASE}/api/v1/agent/run", agent_body, hdrs_no, 401)
test("agent with wrong key", f"{BASE}/api/v1/agent/run", agent_body, hdrs_bad, 401)
test("chat with valid key", f"{BASE}/api/v1/chat", chat_body, hdrs_ok, 200)

print(f"\nRESULTS: {passed} passed, {failed} failed")
