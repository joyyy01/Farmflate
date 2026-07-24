/**
 * Weather data access layer.
 *
 * The app currently has no live weather backend wired up, so `fetchWeather`
 * below returns generated mock data. Once a real weather endpoint exists,
 * replace the body of `fetchWeather` with the actual API call — keep the
 * function name, signature, and `WeatherSnapshot` return shape the same so
 * none of the calling code (hooks/components) needs to change.
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

const CONDITIONS: WeatherCondition[] = ['clear', 'partlyCloudy', 'cloudy', 'rain', 'heavyRain', 'snow'];

function hashString(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = value.charCodeAt(i) + ((hash << 5) - hash);
  }
  return Math.abs(hash);
}

// --- Mock generator ---------------------------------------------------
// Varies by region name and by the current hour, so the card visibly
// changes between hourly refreshes without needing a real backend yet.
function generateMockWeather(regionName: string): WeatherSnapshot {
  const hourBucket = Math.floor(Date.now() / (60 * 60 * 1000));
  const seed = hashString(regionName) + hourBucket;

  const condition = CONDITIONS[seed % CONDITIONS.length];
  const baseTemp = condition === 'snow' ? seed % 5 : 10 + (seed % 20);
  const humidity = 40 + (seed % 45);
  const windSpeed = 1 + (seed % 5);
  const precipitationProbability =
    condition === 'heavyRain' ? 60 + (seed % 35)
      : condition === 'rain' ? 30 + (seed % 40)
        : condition === 'snow' ? 20 + (seed % 30)
          : seed % 30;

  return {
    temperature: baseTemp,
    minTemperature: baseTemp - 3,
    maxTemperature: baseTemp + 4,
    humidity,
    windSpeed,
    precipitationProbability,
    condition,
    conditionLabel: CONDITION_LABELS[condition],
    forecastText: CONDITION_FORECASTS[condition],
    fetchedAt: Date.now()
  };
}

export async function fetchWeather(regionName: string): Promise<WeatherSnapshot> {
  return generateMockWeather(regionName);
}
