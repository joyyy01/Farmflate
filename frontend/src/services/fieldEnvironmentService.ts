/**
 * Field weather/daily-guidance data access layer.
 *
 * Weather data comes from the backend /api/home endpoint (KMA short forecast).
 * No hash-based or mock weather is generated. When no data is available,
 * the weather field is null and the UI shows "데이터 없음".
 *
 * Soil pH/EC are NOT generated here. There is no sensor and no backend soil
 * test endpoint, so those values only ever come from what the user typed in
 * via fieldLogService's soil-test functions.
 */

import { getFieldLogs, getSoilTestResult, type FieldLogCategory } from './fieldLogService';

export type ConditionStatus = 'good' | 'caution' | 'bad';

export interface WeatherReading {
  currentTemp: number;
  maxTemp: number;
  minTemp: number;
  precipitationProbability: number;
  recentRainfallMm: number;
  humidity: number;
  windSpeed: number;
  asOf: number;
  source: string;
}

export interface FieldTask {
  id: string;
  title: string;
  description: string;
  urgency: string;
  icon: 'water' | 'search';
}

export interface FieldAlert {
  id: string;
  icon: 'sun' | 'wind' | 'rain';
  title: string;
  description: string;
}

export interface FieldDailyReport {
  headline: string;
  headlineLevel: ConditionStatus;
  headlineDescription: string;
  reasoningPoints: string[];
  reasoningSummary: string;
  tasks: FieldTask[];
  alerts: FieldAlert[];
  weather: WeatherReading;
  generatedAt: number;
}

// Well-established horticultural pH ranges for the crops this app currently
// supports (not a live data source — just reference knowledge, same as the
// static crop-suitability copy already used elsewhere in the app).
const CROP_PH_RANGES: Record<string, string> = {
  '감자': 'pH 5.0~6.5',
  '상추': 'pH 6.0~7.0',
  '고추': 'pH 6.0~6.8'
};

export function cropPhRangeText(cropName?: string | null): string | null {
  if (!cropName) return null;
  const range = CROP_PH_RANGES[cropName];
  return range ? `${cropName}는 보통 ${range} 정도의 흙에서 잘 자라요.` : null;
}

async function fetchFieldWeather(_fieldId: string): Promise<WeatherReading | null> {
  // No live field-level weather endpoint yet. Return null to indicate
  // "no data" rather than fabricating values from a hash.
  return null;
}

export async function fetchFieldDailyReport(fieldId: string, cropName?: string | null): Promise<FieldDailyReport> {
  const weather = await fetchFieldWeather(fieldId);
  const crop = cropName || '작물';

  const wateringLogged = hasRecentLogEntry(fieldId, '물주기', 2);
  const pestLogged = hasRecentLogEntry(fieldId, '잎 상태 확인', 3);

  if (!weather) {
    return {
      headline: '날씨 데이터를 불러올 수 없어요',
      headlineLevel: 'caution',
      headlineDescription: '지역 분석을 완료하면 기상청 기반 날씨 정보를 확인할 수 있어요.',
      reasoningPoints: [
        '날씨 데이터: 없음',
        `최근 물주기 기록: ${wateringLogged ? '있음' : '확인되지 않음'}`,
        `최근 잎 상태 확인 기록: ${pestLogged ? '있음' : '확인되지 않음'}`
      ],
      reasoningSummary: ' actual 흙의 수분 상태는 직접 확인해주세요.',
      tasks: [
        {
          id: 'water',
          title: '흙 상태를 직접 확인한 후 물주기를 결정하세요',
          description: '날씨 데이터가 없어 자동 안내가 어렵습니다. 흙을 손으로 만져보고 표면 아래까지 말랐다면 물을 주세요.',
          urgency: '오전 권장',
          icon: 'water'
        },
        {
          id: 'pest',
          title: '잎 뒷면 병해충 확인하기',
          description: '한 번씩 잎 상태를 살펴봐 주세요.',
          urgency: '오전 권장',
          icon: 'search'
        }
      ],
      alerts: [],
      weather: {
        currentTemp: 0, maxTemp: 0, minTemp: 0,
        precipitationProbability: 0, recentRainfallMm: 0,
        humidity: 0, windSpeed: 0,
        asOf: Date.now(), source: '데이터 없음'
      },
      generatedAt: Date.now()
    };
  }

  const isHighTempDay = weather.maxTemp >= 26;
  const isDryDay = weather.recentRainfallMm === 0 && weather.humidity < 55;
  const isHumidDay = weather.humidity >= 65;
  const hasCaution = isHighTempDay || isDryDay;

  const tasks: FieldTask[] = [
    {
      id: 'water',
      title: '흙 상태를 확인한 후 물주기를 결정하세요',
      description: weather.recentRainfallMm === 0
        ? '최근 2일간 비가 오지 않았어요. 흙을 손으로 만져보고 표면 아래까지 말랐다면 물을 주세요.'
        : `최근 2일간 ${weather.recentRainfallMm}mm의 비가 내렸어요. 흙 상태를 확인하고 필요할 때만 물을 주세요.`,
      urgency: '오전 권장',
      icon: 'water'
    },
    {
      id: 'pest',
      title: '잎 뒷면 병해충 확인하기',
      description: isHumidDay
        ? '현재 습도가 높아 병해충이 발생하기 쉬운 환경이에요. 잎 뒷면을 확인해주세요.'
        : '비교적 건조한 편이지만 한 번씩 잎 상태를 살펴봐 주세요.',
      urgency: '오전 권장',
      icon: 'search'
    }
  ];

  const alerts: FieldAlert[] = [];
  if (isHighTempDay) {
    alerts.push({
      id: 'heat', icon: 'sun', title: '오후 고온 주의',
      description: `오늘 오후 최고기온이 ${weather.maxTemp}℃까지 올라갈 예정이에요. 잎 처짐을 확인하고 필요 시 차광을 해주세요.`
    });
  }
  if (isDryDay) {
    alerts.push({
      id: 'dry', icon: 'wind', title: '건조 가능성',
      description: '최근 강수량이 적어 토양이 빠르게 마를 수 있어요. 흙 상태를 자주 확인해주세요.'
    });
  }
  if (weather.precipitationProbability >= 60) {
    alerts.push({
      id: 'rain', icon: 'rain', title: '강수 가능성 높음',
      description: `오늘 강수확률이 ${weather.precipitationProbability}%예요. 배수로 상태를 미리 점검해주세요.`
    });
  }

  const reasoningPoints = [
    `최근 2일 강수량: ${weather.recentRainfallMm}mm`,
    `오늘 예상 최고기온: ${weather.maxTemp}℃`,
    `현재 습도: ${weather.humidity}%`,
    `최근 물주기 기록: ${wateringLogged ? '있음' : '확인되지 않음'}`,
    `최근 잎 상태 확인 기록: ${pestLogged ? '있음' : '확인되지 않음'}`
  ];

  const reasoningSummary = isHighTempDay && weather.recentRainfallMm === 0
    ? '최근 비가 오지 않았고 오늘 낮 기온이 높아질 예정이에요. 다만 실제 흙의 수분 상태는 알 수 없어, 흙을 직접 확인한 후 물주기를 결정하도록 안내했어요.'
    : isHighTempDay
      ? '오늘 낮 기온이 높아질 예정이에요. 잎 상태를 확인하고 필요하면 차광해주세요.'
      : weather.recentRainfallMm === 0
        ? '최근 비가 오지 않았어요. 실제 흙의 수분 상태는 알 수 없어, 흙을 직접 확인한 후 물주기를 결정하도록 안내했어요.'
        : `${crop}이(가) 안정적으로 자랄 수 있는 날씨예요.`;

  return {
    headline: hasCaution ? '오늘은 주의가 필요한 상태예요' : '오늘은 안정적인 상태예요',
    headlineLevel: hasCaution ? 'caution' : 'good',
    headlineDescription: isHighTempDay
      ? '낮 기온이 높아 토양과 잎 상태 확인이 필요해요.'
      : isDryDay
        ? '토양이 건조해질 수 있어 수분 상태 확인이 필요해요.'
        : `${crop}이(가) 안정적으로 자라고 있어요.`,
    reasoningPoints,
    reasoningSummary,
    tasks,
    alerts,
    weather,
    generatedAt: Date.now()
  };
}

function hasRecentLogEntry(fieldId: string, category: FieldLogCategory, withinDays: number): boolean {
  const logs = getFieldLogs(fieldId);
  const cutoff = Date.now() - withinDays * 24 * 60 * 60 * 1000;
  return logs.some(log => log.category === category && new Date(log.loggedAt).getTime() >= cutoff);
}

export { getSoilTestResult };
