from __future__ import annotations

import asyncio
from unittest.mock import AsyncMock, patch

from app.integration.region_weather import HttpRegionWeatherClient


class _FakeResponse:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, object]:
        return {
            "status": "AVAILABLE",
            "unavailableReason": None,
            "days": [{
                "date": "2026-07-30",
                "minTemperature": 23.0,
                "maxTemperature": 31.0,
                "precipitationProbability": 20,
                "rainfallMm": 0.0,
                "humidity": 70.0,
                "windSpeed": 2.1,
            }],
        }


def test_region_weather_client_uses_normalized_codes_and_internal_token() -> None:
    captured: dict[str, object] = {}

    async def get(url: str, **kwargs: object) -> _FakeResponse:
        captured["url"] = url
        captured.update(kwargs)
        return _FakeResponse()

    client = HttpRegionWeatherClient(base_url="http://spring:8080/", token="test-token")
    with patch("app.integration.region_weather.outbound_http_client.get", new_callable=AsyncMock, side_effect=get):
        result = asyncio.run(client.read(sido_code="41", sigungu_code="41135", days=1))

    assert captured["url"] == "http://spring:8080/api/internal/weather/regions/41/41135"
    assert captured["headers"] == {"X-Internal-Service-Token": "test-token"}
    assert captured["params"] == {"days": 1}
    assert result.status == "AVAILABLE"
    assert result.days[0].max_temperature == 31.0
