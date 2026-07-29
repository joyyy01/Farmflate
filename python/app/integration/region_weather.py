from __future__ import annotations

import re
from typing import Protocol

from pydantic import BaseModel, ConfigDict, Field

from app.core.outbound_http import outbound_http_client


class RegionWeatherInputError(ValueError):
    pass


class RegionWeatherDay(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    date: str
    min_temperature: float | None = Field(default=None, alias="minTemperature")
    max_temperature: float | None = Field(default=None, alias="maxTemperature")
    precipitation_probability: int | None = Field(default=None, alias="precipitationProbability")
    rainfall_mm: float | None = Field(default=None, alias="rainfallMm")
    humidity: float | None = None
    wind_speed: float | None = Field(default=None, alias="windSpeed")


class RegionWeatherSnapshot(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: str
    unavailable_reason: str | None = Field(default=None, alias="unavailableReason")
    days: list[RegionWeatherDay] = Field(default_factory=list)


class RegionWeatherClient(Protocol):
    async def read(self, *, sido_code: str, sigungu_code: str, days: int) -> RegionWeatherSnapshot: ...


class HttpRegionWeatherClient:
    def __init__(self, *, base_url: str, token: str) -> None:
        self._base_url = base_url.rstrip("/")
        self._token = token

    async def read(self, *, sido_code: str, sigungu_code: str, days: int) -> RegionWeatherSnapshot:
        if not re.fullmatch(r"[0-9]{2}", sido_code) or not re.fullmatch(r"[0-9]{5}", sigungu_code):
            raise RegionWeatherInputError("invalid_region_code")
        if days not in (1, 2, 3):
            raise RegionWeatherInputError("invalid_days")

        response = await outbound_http_client.get(
            f"{self._base_url}/api/internal/weather/regions/{sido_code}/{sigungu_code}",
            headers={"X-Internal-Service-Token": self._token},
            params={"days": days},
        )
        response.raise_for_status()
        return RegionWeatherSnapshot.model_validate(response.json())
