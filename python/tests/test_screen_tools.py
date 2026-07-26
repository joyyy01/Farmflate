import unittest

from app.services.screen_tools import compare_visible_crops, resolve_visible_target


class ScreenToolsTest(unittest.TestCase):
    def test_resolves_visible_soil_ph_when_question_names_ph(self) -> None:
        result = resolve_visible_target(
            "이 pH가 낮다는 건 무슨 뜻인가요?",
            [{"key": "component.soil.soilPh", "label": "토양 pH", "section": "soil"}],
            {"component.soil.soilPh": 5.2},
            [],
        )

        self.assertEqual(result.status, "resolved")
        self.assertEqual(result.fact_keys, ("component.soil.soilPh",))

    def test_requests_clarification_when_this_score_matches_multiple_visible_scores(self) -> None:
        result = resolve_visible_target(
            "이 점수는 왜 이렇게 나왔나요?",
            [
                {"key": "region.score", "label": "종합 적합도 점수", "section": "summary"},
                {"key": "component.soil.score", "label": "토양 적합도", "section": "soil"},
            ],
            {"region.score": 71, "component.soil.score": 62},
            [],
        )

        self.assertEqual(result.status, "ambiguous")
        self.assertIn("종합 적합도", result.clarification or "")

    def test_compares_only_the_visible_crop_facts(self) -> None:
        result = compare_visible_crops(
            ("crop.1", "crop.2"),
            {
                "crop.1.name": "상추",
                "crop.1.score": 82,
                "crop.2.name": "오이",
                "crop.2.score": 76,
                "crop.3.name": "감자",
                "crop.3.score": 95,
            },
        )

        self.assertEqual(result["crops"], [{"name": "상추", "score": 82}, {"name": "오이", "score": 76}])
        self.assertNotIn("감자", str(result))


if __name__ == "__main__":
    unittest.main()
