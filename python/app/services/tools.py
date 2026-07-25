from __future__ import annotations

from typing import Any

AGRICULTURAL_GLOSSARY: dict[str, str] = {
    "EC": "전기전도도(Electrical Conductivity). 토양 내 용해된 염류 농도를 나타내며, 일반적으로 dS/m 단위를 사용합니다. EC가 높으면 염류 장해가 발생할 수 있습니다.",
    "pH": "토양 산도. 7을 중성으로 하며, 낮으면 산성, 높으면 알칼리성입니다. 대부분의 작물은 pH 5.5~7.0에서 잘 자랍니다.",
    "유기물": "토양 내 동식물 잔해가 분해된 유기 물질. 토양 구조, 보수력, 양분 공급에 중요합니다. 일반적으로 2~5%가 적정 범위입니다.",
    "질소": "식물 생장에 필수적인 다량 원소(N). 잎과 줄기 생장을 촉진하며, 부족 시 하엽 황화 현상이 나타납니다.",
    "인산": "식물 생장에 필수적인 다량 원소(P). 뿌리 발달, 개화, 결실에 중요합니다.",
    "칼륨": "식물 생장에 필수적인 다량 원소(K). 병해 저항성과 내한성 강화에 기여합니다.",
    "칼슘": "중량 원소(Ca). 세포벽 형성과 뿌리 생장에 필요하며, 부족 시 생장점 괴사가 발생할 수 있습니다.",
    "마그네슘": "중량 원소(Mg). 엽록소의 구성 성분으로, 부족 시 잎맥 사이 황화 현상이 나타납니다.",
    "유효토심": "작물 뿌리가 뻗을 수 있는 토양 깊이. 배수·통기성이 양호한 층의 두께를 의미합니다.",
    "배수": "토양에서 잉여 수분이 빠져나가는 능력. 배수 불량은 뿌리 부패와 습해를 유발합니다.",
    "적산온도": "일정 기간 동안 일평균 기온을 누적한 값(GDD, Growing Degree Days). 작물 생육 단계 예측에 사용됩니다.",
    "생육적온": "작물이 가장 잘 자라는 온도 범위. 이 범위를 벗어나면 생육이 저하됩니다.",
    "토양산도": "토양의 산성·알칼리성 정도(pH). 양분 용해도와 미생물 활동에 직접 영향을 미칩니다.",
    "염기치환": "토양 입자에 흡착된 양이온(K, Ca, Mg 등)의 총량. 토양 비옥도 지표 중 하나입니다.",
    "부식": "유기물이 미생물에 의해 분해·안정화된 암갈색 물질. 토양 구조 개선과 양분 보유에 기여합니다.",
    "점토": "입경 0.002mm 미만의 토양 입자. 보수력과 양분 보유력이 높으나 통기성이 낮습니다.",
    "미사": "입경 0.002~0.05mm의 토양 입자. 점토와 모래의 중간 성질을 가집니다.",
    "사질": "모래 함량이 높은 토양. 배수와 통기성이 좋으나 보수력과 양분 보유력이 낮습니다.",
    "양토": "모래·미사·점토가 적절히 혼합된 토양. 대부분의 작물에 이상적인 토성입니다.",
    "식양토": "점토 함량이 높은 양토. 보수력이 좋으나 배수 관리가 필요합니다.",
}


def get_region_analysis(facts: dict[str, Any]) -> dict[str, Any]:
    region_facts: dict[str, Any] = {}
    for key, value in facts.items():
        if key.startswith("region.") or key.startswith("crop.") or key.startswith("risk."):
            region_facts[key] = value
    return region_facts


def get_report_sources(sources: list[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "sourceId": s.get("sourceId", ""),
            "provider": s.get("provider", ""),
            "service": s.get("service", ""),
            "dataDate": s.get("dataDate", ""),
            "sourceUrl": s.get("sourceUrl", ""),
        }
        for s in sources
        if isinstance(s, dict)
    ]


def get_field_report(facts: dict[str, Any]) -> dict[str, Any] | str:
    field_facts = {k: v for k, v in facts.items() if k.startswith("field.")}
    if not field_facts:
        return "TOOL_CONTEXT_UNAVAILABLE"
    return field_facts


def explain_agricultural_term(term: str) -> dict[str, Any]:
    normalized = term.strip()
    for key, definition in AGRICULTURAL_GLOSSARY.items():
        if key.lower() == normalized.lower() or normalized.lower() in key.lower():
            return {"term": key, "definition": definition, "found": True}
    return {"term": normalized, "definition": None, "found": False}


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
