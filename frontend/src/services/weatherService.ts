/**
 * Weather data access layer.
 *
 * Maps the backend /api/home weather DTO to the WeatherSnapshot shape
 * consumed by the dashboard illustration and condition labels.
 * No mock data — all values come from the Spring Boot backend
 * (KMA short forecast via RegionAnalysisService).
 */

export type WeatherCondition =
  | 'clear'
  | 'partlyCloudy'
  | 'cloudy'
  | 'rain'
  | 'heavyRain'
  | 'snow';

export interface WeatherSnapshot {
  temperature: number;
  minTemperature: number;
  maxTemperature: number;
  humidity: number;
  windSpeed: number;
  precipitationProbability: number;
  condition: WeatherCondition;
  conditionLabel: string;
  forecastText: string;
  fetchedAt: number;
}

export const WEATHER_ILLUSTRATIONS: Record<WeatherCondition, string> = {
  clear: '/assets/weather_clear.png',
  partlyCloudy: '/assets/weather_partly_cloudy.png',
  cloudy: '/assets/weather_cloudy.png',
  rain: '/assets/weather_rain.png',
  heavyRain: '/assets/weather_heavy_rain.png',
  snow: '/assets/weather_snow.png'
};

const CONDITION_LABELS: Record<WeatherCondition, string> = {
  clear: '맑음',
  partlyCloudy: '구름 조금',
  cloudy: '흐림',
  rain: '비 소식',
  heavyRain: '집중호우',
  snow: '눈 소식'
};

const CONDITION_FORECASTS: Record<WeatherCondition, string> = {
  clear: '오늘 일조량이 풍부해요',
  partlyCloudy: '나들이하기 좋은 날씨예요',
  cloudy: '구름이 많이 껴 있어요',
  rain: '외출 시 우산을 챙기세요',
  heavyRain: '집중호우에 유의하세요',
  snow: '노면 결빙에 유의하세요'
};

/** Maps the backend condition enum to the frontend illustration enum. */
export function mapBackendCondition(backendCondition: string | null | undefined): WeatherCondition {
  switch ((backendCondition ?? '').toUpperCase()) {
    case 'SUNNY': return 'clear';
    case 'RAIN': return 'rain';
    case 'CLOUDY': return 'cloudy';
    case 'SNOW': return 'snow';
    default: return 'clear';
  }
}

/** Builds a WeatherSnapshot from the backend home weather DTO. */
export function snapshotFromBackend(weather: {
  temperature?: number | null;
  minTemperature?: number | null;
  maxTemperature?: number | null;
  precipitationProbability?: number | null;
  condition?: string | null;
  status?: string | null;
} | null | undefined): WeatherSnapshot | null {
  if (!weather || weather.status === 'UNAVAILABLE') return null;
  const condition = mapBackendCondition(weather.condition);
  const temp = weather.temperature ?? 0;
  return {
    temperature: temp,
    minTemperature: weather.minTemperature ?? temp - 3,
    maxTemperature: weather.maxTemperature ?? temp + 4,
    humidity: 0,
    windSpeed: 0,
    precipitationProbability: weather.precipitationProbability ?? 0,
    condition,
    conditionLabel: CONDITION_LABELS[condition],
    forecastText: CONDITION_FORECASTS[condition],
    fetchedAt: Date.now(),
  };
}
