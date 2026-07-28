from __future__ import annotations

from datetime import datetime, timezone, timedelta
from typing import Any

KST = timezone(timedelta(hours=9))

# ── Agricultural Glossary ──────────────────────────────────────────────
AGRICULTURAL_GLOSSARY: dict[str, str] = {
    "EC": "전기전도도(Electrical Conductivity). 토양 내 용해된 염류 농도를 나타내며, dS/m 단위를 사용합니다. EC가 높으면 염류 장해가 발생할 수 있습니다. 일반 적정 범위는 0.5~2.0 dS/m입니다.",
    "pH": "토양 산도. 7을 중성으로 하며, 낮으면 산성, 높으면 알칼리성입니다. 대부분의 작물은 pH 5.5~7.0에서 잘 자랍니다. pH가 낮으면 석회 시용, 높으면 유황 시용으로 교정합니다.",
    "유기물": "토양 내 동식물 잔해가 분해된 유기 물질. 토양 구조, 보수력, 양분 공급에 중요합니다. 적정 범위 2~5%이며, 부족 시 퇴비·부엽토를 투입합니다.",
    "질소": "식물 생장 필수 다량 원소(N). 잎·줄기 생장을 촉진하며, 부족 시 하엽 황화, 과다 시 도장·병해 취약. 원소 기호 N.",
    "인산": "식물 생장 필수 다량 원소(P). 뿌리 발달, 개화, 결실에 중요. 부족 시 잎이 암녹색·자주색으로 변합니다.",
    "칼륨": "식물 생장 필수 다량 원소(K). 병해 저항성·내한성 강화. 부족 시 잎 가장자리 갈변(엽소)이 나타납니다.",
    "칼슘": "중량 원소(Ca). 세포벽 형성·뿌리 생장에 필요. 부족 시 생장점 괴사, 과실 배꼽썩음병(석회결핍) 발생.",
    "마그네슘": "중량 원소(Mg). 엽록소 구성 성분. 부족 시 잎맥 사이 황화(맥간 황화)가 나타납니다.",
    "유효토심": "작물 뿌리가 뻗을 수 있는 토양 깊이. 배수·통기성이 양호한 층의 두께. 과수는 60cm 이상, 채소는 30cm 이상 권장.",
    "배수": "토양에서 잉여 수분이 빠져나가는 능력. 배수 불량은 뿌리 부패·습해 유발. 개선: 암거배수, 두둑 재배, 유기물 투입.",
    "적산온도": "일정 기간 일평균 기온 누적값(GDD). 작물 생육 단계 예측에 사용. 기준 온도 이상만 누적합니다.",
    "생육적온": "작물이 가장 잘 자라는 온도 범위. 이 범위를 벗어나면 생육 저하, 극단 시 고사.",
    "토양산도": "토양의 산성·알칼리성 정도(pH). 양분 용해도·미생물 활동에 직접 영향.",
    "염기치환": "토양 입자에 흡착된 양이온(K, Ca, Mg 등) 총량(CEC). 토양 비옥도 지표.",
    "부식": "유기물이 미생물에 의해 분해·안정화된 암갈색 물질. 토양 구조 개선·양분 보유에 기여.",
    "점토": "입경 0.002mm 미만 토양 입자. 보수력·양분 보유력 높으나 통기성 낮음.",
    "미사": "입경 0.002~0.05mm 토양 입자. 점토와 모래의 중간 성질.",
    "사질": "모래 함량이 높은 토양. 배수·통기성 좋으나 보수력·양분 보유력 낮음.",
    "양토": "모래·미사·점토가 적절히 혼합된 토양. 대부분 작물에 이상적.",
    "식양토": "점토 함량이 높은 양토. 보수력 좋으나 배수 관리 필요.",
    "도장": "영양 생장이 과도하여 줄기·잎만 무성하고 결실이 부진한 현상. 질소 과다 시 발생.",
    "엽소": "잎 가장자리가 갈색으로 타는 현상. 칼륨 부족, 염류 집적, 고온 건조 시 발생.",
    "습해": "토양 과습으로 뿌리가 산소 부족을 겪는 장해. 배수 불량 시 발생.",
    "연작장해": "같은 작물을 연속 재배 시 토양 양분 불균형·병해충 누적으로 생육이 나빠지는 현상.",
}

# ── Crop Knowledge Base ────────────────────────────────────────────────
CROP_PROFILES: dict[str, dict[str, Any]] = {
    "APPLE": {
        "name": "사과", "emoji": "🍎",
        "temp_optimal": "18~24℃", "ph_optimal": "5.8~6.3",
        "planting": "3~4월 (묘목) / 10~11월 (가을 심기)",
        "harvest": "8~11월 (품종별 상이)",
        "watering": "개화기·과실 비대기에 주 2~3회, 과습 주의",
        "soil_note": "배수 양호한 사질양토 최적. pH 5.8 미만 시 석회 시용.",
        "key_risks": ["서리(개화기)", "탄저병(장마기)", "일소(폭염)", "조류 피해"],
        "seasonal": {
            1: "동계 전정(가지치기) 시기. 병해충 월동 방제.",
            2: "전정 마무리. 석회·퇴비 기비 시용.",
            3: "묘목 정식. 발아 전 기계유유제 살포.",
            4: "개화기. 서리 피해 주의. 인공수분 준비.",
            5: "적과(열매 솎기). 신초 관리.",
            6: "장마 대비 배수로 정비. 탄저병 예방 살포.",
            7: "하계 전정. 과실 비대기 관수 관리.",
            8: "착색 관리. 반사필름 피복. 태풍 대비.",
            9: "수확 시작(조생종). 당도 확인.",
            10: "본격 수확. 수확 후 시비.",
            11: "낙엽 후 동계 방제. 월동 준비.",
            12: "동계 전정 시작. 저장고 관리.",
        },
    },
    "PEAR": {
        "name": "배", "emoji": "🍐",
        "temp_optimal": "20~25℃", "ph_optimal": "5.5~6.5",
        "planting": "3~4월 (묘목)",
        "harvest": "8~10월 (품종별)",
        "watering": "과실 비대기(6~8월)에 주 2~3회. 과습 시 당도 저하.",
        "soil_note": "깊은 토층(60cm+)의 양토·식양토. 배수 필수.",
        "key_risks": ["개화기 저온(서리)", "흑성병(장마)", "꼬마배나무이", "태풍 낙과"],
        "seasonal": {
            1: "동계 전정. 석회유황합제 살포.",
            2: "전정 마무리. 기비 시용.",
            3: "개화 전 인공수분 준비. 꽃눈 확인.",
            4: "개화기. 서리 주의! 인공수분 실시.",
            5: "적과. 봉지 씌우기 준비.",
            6: "봉지 씌우기. 장마 대비 배수 관리.",
            7: "과실 비대기. 관수·추비.",
            8: "수확(조생종). 태풍 대비 지주 보강.",
            9: "본격 수확. 당도·경도 확인.",
            10: "수확 마무리. 수확 후 시비.",
            11: "낙엽 후 동계 방제.",
            12: "동계 전정 시작.",
        },
    },
    "CUCUMBER": {
        "name": "오이", "emoji": "🥒",
        "temp_optimal": "20~25℃", "ph_optimal": "5.5~6.8",
        "planting": "4~5월 (노지) / 2~3월 (시설)",
        "harvest": "6~9월 (정식 후 40~50일)",
        "watering": "다습 선호. 하루 1~2회 관수. 과실 비대기 물 부족 시 기형과 발생.",
        "soil_note": "보수력 좋은 양토. 유기물 풍부하게. pH 5.5 미만 시 석회.",
        "key_risks": ["저온(15℃ 이하)", "흰가루병", "노균병(다습)", "진딧물"],
        "seasonal": {
            1: "시설재배: 육묘 관리. 보온 관리.",
            2: "시설재배: 정식 준비. 토양 소독.",
            3: "시설재배: 정식. 보온·환기 관리.",
            4: "노지재배: 파종·정식. 지주 설치.",
            5: "유인·적엽. 추비 시작.",
            6: "수확 시작. 관수·추비 주기적.",
            7: "고온기 환기 관리. 병해충 방제.",
            8: "수확 계속. 노균병·흰가루병 주의.",
            9: "수확 마무리. 후작 준비.",
            10: "시설재배: 정식 준비.",
            11: "시설재배: 보온 관리.",
            12: "시설재배: 수확. 최저 온도 12℃ 유지.",
        },
    },
    "POTATO": {
        "name": "감자", "emoji": "🥔",
        "temp_optimal": "15~20℃", "ph_optimal": "5.0~6.0",
        "planting": "2~3월 (봄) / 8월 (가을)",
        "harvest": "5~6월 (봄) / 10~11월 (가을)",
        "watering": "건조 시 주 1~2회. 과습 시 괴경 부패. 수확 2주 전 관수 중단.",
        "soil_note": "사질양토 최적. pH 5.0~6.0(약산성). 석회 과다 시 더뎅이병.",
        "key_risks": ["역병(장마기)", "더뎅이병(알칼리 토양)", "청고병(연작)", "진딧물(바이러스 매개)"],
        "seasonal": {
            1: "봄 재배: 씨감자 준비. 싹틔우기(최아).",
            2: "봄 재배: 정식(2월 하순~3월). 두둑·비닐 피복.",
            3: "싹 출현. 북주기 1차.",
            4: "북주기 2차. 추비. 진딧물 방제.",
            5: "개화기. 역병 예방 살포. 관수 관리.",
            6: "수확(잎 70% 황화 시). 수확 전 관수 중단.",
            7: "수확 후 건조·선별. 저장.",
            8: "가을 재배: 씨감자 준비. 정식.",
            9: "가을 재배: 북주기. 병해충 관리.",
            10: "가을 재배: 수확.",
            11: "저장 관리. 이듬해 계획.",
            12: "씨감자 확보. 저장고 점검.",
        },
    },
    "LETTUCE": {
        "name": "상추", "emoji": "🥬",
        "temp_optimal": "15~20℃", "ph_optimal": "6.0~7.0",
        "planting": "3~5월 (봄) / 8~9월 (가을)",
        "harvest": "정식 후 30~40일 (연중 수확 가능)",
        "watering": "다습 선호. 하루 1회 관수. 과습 시 무름병. 수확 전일 관수 시 신선도 ↑.",
        "soil_note": "보수력 좋은 양토. pH 6.0~7.0. 질소질 비료 중심.",
        "key_risks": ["고온(25℃ 이상 추대)", "무름병(다습)", "진딧물", "균핵병"],
        "seasonal": {
            1: "시설재배: 파종·정식. 보온 관리.",
            2: "시설재배: 수확. 환기 관리.",
            3: "노지재배: 파종. 터널 피복.",
            4: "노지재배: 정식. 진딧물 방제.",
            5: "수확 시작. 추대(꽃대) 주의.",
            6: "고온기: 차광망 설치. 수확.",
            7: "고온기: 시설재배 위주. 환기 필수.",
            8: "가을 재배: 파종. 고온기 육묘 관리.",
            9: "가을 재배: 정식.",
            10: "수확. 서리 전 수확 마무리.",
            11: "시설재배: 파종·정식.",
            12: "시설재배: 수확. 보온 관리.",
        },
    },
}

# ── Risk Mitigation Guide ──────────────────────────────────────────────
RISK_GUIDES: dict[str, dict[str, str]] = {
    "HEAT": {
        "title": "폭염·고온",
        "action": "차광망(30~50%) 설치, 이른 아침·저녁 관수, 멀칭으로 지온 상승 억제. 한낮 작업 자제.",
        "detail": "33℃ 이상 시 작물 광합성 저하, 과실 일소, 시들음 발생. 과수는 미세살수, 채소는 차광이 효과적.",
    },
    "COLD_FROST": {
        "title": "저온·서리",
        "action": "부직포·비닐 터널 피복, 방상팬 가동, 관수(잠열 이용). 개화기 서리 시 인공수분 재실시.",
        "detail": "5℃ 이하 시 세포 동결 피해. 개화기 서리는 결실률 급감. 과수는 -2℃에서도 꽃눈 피해.",
    },
    "CONCENTRATED_RAIN": {
        "title": "집중호우",
        "action": "배수로 사전 정비, 두둑 높이기, 비닐 피복 고정. 호우 후 역병·무름병 예방 살포.",
        "detail": "30mm/h 이상 시 침수·유실 위험. 과수원 경사면 토양 유실 주의. 호우 후 24시간 내 방제.",
    },
    "WIND": {
        "title": "강풍",
        "action": "지주·유인줄 보강, 방풍망 설치, 시설하우스 비닐 고정. 태풍 예보 시 조기 수확.",
        "detail": "9m/s 이상 시 낙과·도복·시설 파손. 과수 낙과율 30% 이상 가능.",
    },
    "HEAVY_RAIN": {
        "title": "호우",
        "action": "배수로 정비, 두둑 재설치. 호우 후 뿌리 노출부 복토, 살균제 살포.",
        "detail": "일 30mm 이상 강수 시 습해·병해 위험 증가. 과수원 배수 필수.",
    },
    "DROUGHT": {
        "title": "가뭄",
        "action": "관수 시설 점검, 멀칭(비닐·짚)으로 수분 증발 억제. 이른 아침 관수.",
        "detail": "3일 이상 무강수 + 고온 시 토양 수분 부족. 과실 비대 저하, 시들음.",
    },
    "HIGH_HUMIDITY": {
        "title": "다습",
        "action": "환기 강화, 잎 사이 통풍 확보, 하엽 제거. 예방적 살균제 살포.",
        "detail": "습도 85% 이상 지속 시 곰팡이병(노균병, 흰가루병, 역병) 발생 위험 급증.",
    },
}


def get_region_analysis(facts: dict[str, Any]) -> dict[str, Any]:
    return {k: v for k, v in facts.items()
            if k.startswith(("region.", "crop.", "risk."))}


def get_report_sources(sources: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "sourceId": s.get("sourceId", ""),
            "title": s.get("title", f"{s.get('provider', '')} {s.get('service', '')}"),
            "provider": s.get("provider", ""),
            "service": s.get("service", ""),
            "observedAt": s.get("observedAt", s.get("dataDate", "")),
            "sourceUrl": s.get("sourceUrl", ""),
        }
        for s in sources if isinstance(s, dict)
    ]


def get_field_report(facts: dict[str, Any]) -> dict[str, Any] | str:
    field_facts = {k: v for k, v in facts.items() if k.startswith("field.")}
    return field_facts if field_facts else "TOOL_CONTEXT_UNAVAILABLE"


def explain_agricultural_term(term: str) -> dict[str, Any]:
    normalized = term.strip()
    for key, definition in AGRICULTURAL_GLOSSARY.items():
        if key.lower() == normalized.lower() or normalized.lower() in key.lower():
            return {"term": key, "definition": definition, "found": True}
    return {"term": normalized, "definition": None, "found": False}


def get_crop_profile(crop_name: str) -> dict[str, Any] | None:
    for code, profile in CROP_PROFILES.items():
        if profile["name"] == crop_name or code.lower() == crop_name.lower():
            return {"code": code, **profile}
    return None


def get_seasonal_advice(crop_name: str, month: int | None = None) -> dict[str, Any] | None:
    profile = get_crop_profile(crop_name)
    if not profile:
        return None
    m = month or datetime.now(KST).month
    advice = profile.get("seasonal", {}).get(m, "")
    return {
        "crop": profile["name"],
        "month": m,
        "advice": advice,
        "temp_optimal": profile["temp_optimal"],
        "watering": profile["watering"],
    }


def get_risk_guide(risk_code: str) -> dict[str, Any] | None:
    code_upper = risk_code.upper() if risk_code else ""
    for key, guide in RISK_GUIDES.items():
        if key == code_upper or key in code_upper:
            return {"code": key, **guide}
    return None


def compare_crops(facts: dict[str, Any]) -> list[dict[str, Any]]:
    crops = []
    for i in range(1, 6):
        name = facts.get(f"crop.{i}.name")
        score = facts.get(f"crop.{i}.score")
        if name:
            profile = get_crop_profile(str(name))
            crops.append({
                "rank": i,
                "name": str(name),
                "score": score,
                "temp_optimal": profile["temp_optimal"] if profile else None,
                "ph_optimal": profile["ph_optimal"] if profile else None,
                "planting": profile["planting"] if profile else None,
                "harvest": profile["harvest"] if profile else None,
            })
    return crops


def search_official_guidance(
    facts: dict[str, Any],
    sources: list[dict[str, Any]],
    crop_code: str | None = None,
    query: str | None = None,
) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for key, value in facts.items():
        if key.startswith("tip.") and isinstance(value, str) and value.strip():
            results.append({"type": "tip", "factKey": key, "content": value.strip()})
    for source in sources:
        if isinstance(source, dict):
            results.append({
                "type": "source",
                "provider": source.get("provider", ""),
                "service": source.get("service", ""),
                "sourceUrl": source.get("sourceUrl", ""),
            })
    return results
