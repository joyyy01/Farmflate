"""Pure read-only helpers for resolving the data currently visible to a user.

The helpers only inspect the already-authorized FactPackage.  They deliberately
do not import HTTP clients, repositories, or services with side effects.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal


@dataclass(frozen=True)
class TargetResolution:
    status: Literal["resolved", "ambiguous", "missing"]
    fact_keys: tuple[str, ...] = ()
    label: str | None = None
    clarification: str | None = None


def _normalise_visible_data(visible_data: Any, facts: dict[str, Any]) -> list[dict[str, str]]:
    if not isinstance(visible_data, list):
        return []
    refs: list[dict[str, str]] = []
    seen: set[str] = set()
    for raw in visible_data[:12]:
        if not isinstance(raw, dict):
            continue
        key = raw.get("key")
        label = raw.get("label")
        section = raw.get("section")
        if not all(isinstance(value, str) and value.strip() for value in (key, label, section)):
            continue
        concrete = _concrete_fact_keys(key.strip(), facts)
        if not concrete or key in seen:
            continue
        seen.add(key)
        refs.append({"key": key.strip(), "label": label.strip()[:80], "section": section.strip()})
    return refs


def _concrete_fact_keys(semantic_key: str, facts: dict[str, Any]) -> tuple[str, ...]:
    if semantic_key in facts:
        return (semantic_key,)
    prefix_map = {
        "crop.": ("name", "score", "baseFitness", "seasonReadiness", "caution", "reason.1"),
        "risk.": ("title", "code", "action.1"),
        "field.alert.": ("title", "severity", "description"),
        "field.task.": ("title", "description"),
    }
    for prefix, suffixes in prefix_map.items():
        if semantic_key.startswith(prefix):
            return tuple(
                f"{semantic_key}.{suffix}"
                for suffix in suffixes
                if f"{semantic_key}.{suffix}" in facts
            )
    return ()


def _refs_matching_keyword(refs: list[dict[str, str]], keyword: str) -> list[dict[str, str]]:
    normalized = keyword.replace(" ", "").lower()
    return [
        ref for ref in refs
        if normalized in ref["key"].replace(" ", "").lower()
        or normalized in ref["label"].replace(" ", "").lower()
    ]


def _clarify(refs: list[dict[str, str]]) -> TargetResolution:
    labels = ", ".join(ref["label"] for ref in refs[:3])
    return TargetResolution(
        status="ambiguous",
        clarification=f"어느 항목을 말씀하시는지 알려주세요. 현재 화면에는 {labels}가 표시돼 있어요.",
    )


def resolve_visible_target(
    question: str,
    visible_data: Any,
    facts: dict[str, Any],
    history: list[dict[str, Any]] | None = None,
) -> TargetResolution:
    """Resolve user language to visible, server-authorized Fact keys."""
    refs = _normalise_visible_data(visible_data, facts)
    if not refs:
        return TargetResolution(status="missing", clarification="현재 화면에서 설명할 분석 항목을 찾지 못했어요.")

    normalized = question.replace(" ", "").lower()
    if any(token in normalized for token in ("첫번째", "1위", "첫째")):
        candidates = [ref for ref in refs if ref["key"] == "crop.1"]
    elif any(token in normalized for token in ("두번째", "2위", "둘째")):
        candidates = [ref for ref in refs if ref["key"] == "crop.2"]
    elif "ph" in normalized or "산도" in normalized:
        candidates = [ref for ref in refs if "ph" in ref["key"].lower() or "ph" in ref["label"].lower()]
    elif "ec" in normalized or "전기전도도" in normalized:
        candidates = [ref for ref in refs if "ec" in ref["key"].lower() or "ec" in ref["label"].lower()]
    elif any(token in normalized for token in ("경고", "위험", "고온", "저온", "서리")):
        candidates = [ref for ref in refs if ref["key"].startswith(("risk.", "field.alert."))]
    elif "점수" in normalized:
        candidates = [ref for ref in refs if "score" in ref["key"].lower() or "점수" in ref["label"]]
    elif any(token in normalized for token in ("왜이렇게", "안내이유", "분석근거")):
        candidates = [ref for ref in refs if ref["key"].startswith("field.reasoning.")]
    else:
        candidates = [
            ref for ref in refs
            if ref["label"].replace(" ", "").lower() in normalized
            or ref["key"].replace(".", "") in normalized
        ]

    if len(candidates) == 1:
        ref = candidates[0]
        return TargetResolution(
            status="resolved",
            fact_keys=_concrete_fact_keys(ref["key"], facts),
            label=ref["label"],
        )
    if len(candidates) > 1:
        return _clarify(candidates)
    return TargetResolution(
        status="missing",
        clarification="현재 화면에서 질문과 일치하는 항목을 찾지 못했어요. 점수, 토양 pH, 경고, 추천 작물 중 어느 항목인지 알려주세요.",
    )


def explain_visible_metric(target: TargetResolution, facts: dict[str, Any]) -> dict[str, Any]:
    related_keys = list(target.fact_keys)
    if any(key.startswith("field.reasoning.") for key in target.fact_keys):
        related_keys.extend(key for key in (
            "field.crop.name", "field.alert.1.title", "field.task.1.title",
        ) if key in facts)
    return {
        "label": target.label or "화면 항목",
        "facts": {key: facts[key] for key in dict.fromkeys(related_keys) if key in facts},
        "used_fact_ids": list(dict.fromkeys(key for key in related_keys if key in facts)),
    }


def summarize_report_evidence(target: TargetResolution, facts: dict[str, Any], sources: list[dict[str, Any]]) -> dict[str, Any]:
    related_keys = list(target.fact_keys)
    if any(key.startswith(("risk.", "field.alert.")) for key in target.fact_keys):
        related_keys.extend(key for key in facts if key.startswith(("field.weather.", "risk.1.action.")))
    return {
        "label": target.label or "화면 항목",
        "facts": {key: facts[key] for key in dict.fromkeys(related_keys) if key in facts},
        "source_ids": [str(source.get("sourceId")) for source in sources if isinstance(source, dict) and source.get("sourceId")],
        "used_fact_ids": list(dict.fromkeys(key for key in related_keys if key in facts)),
    }


def compare_visible_crops(target_keys: tuple[str, ...], facts: dict[str, Any]) -> dict[str, Any]:
    crops: list[dict[str, Any]] = []
    used_fact_ids: list[str] = []
    for key in target_keys[:2]:
        if not key.startswith("crop."):
            continue
        name_key, score_key = f"{key}.name", f"{key}.score"
        if name_key not in facts:
            continue
        crops.append({"name": facts[name_key], "score": facts.get(score_key)})
        used_fact_ids.append(name_key)
        if score_key in facts:
            used_fact_ids.append(score_key)
    return {"crops": crops, "used_fact_ids": used_fact_ids}


def recommend_next_checks(target: TargetResolution, facts: dict[str, Any]) -> dict[str, Any]:
    keys = [
        key for key in (
            "field.task.1.title", "field.task.1.description", "field.alert.1.title",
            "risk.1.title", "risk.1.action.1", "data.confidence.message", "data.missing.1",
        ) if key in facts
    ]
    return {"facts": {key: facts[key] for key in keys}, "used_fact_ids": keys, "target": target.label}
