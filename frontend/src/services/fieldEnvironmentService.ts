/**
 * Field environment/daily-report data access layer.
 *
 * The backend currently only exposes field CRUD (GET/POST /api/fields) — there is
 * no endpoint yet for a field's live sensor readings, per-task checklist, or its
 * recent management history, so everything below is generated mock data seeded by
 * the field id (stable per field, changes once per day). Once those endpoints
 * exist, replace the body of `fetchFieldEnvironment` with the real API call and
 * keep this file's exported shapes the same so FieldDashboardView doesn't need to
 * change.
 */

export type ConditionStatus = 'good' | 'caution' | 'bad';

export interface SoilReading {
  value: number;
  unit: string;
  status: ConditionStatus;
  description: string;
}

export interface FieldEnvironmentSnapshot {
  currentTemp: number;
  maxTemp: number;
  minTemp: number;
  precipitationProbability: number;
  precipitationAmount: number;
  humidity: number;
  windSpeed: number;
  uvIndex: number;
  sunshineHours: number;
  soilPh: SoilReading;
  soilEc: SoilReading;
  soilTemp: SoilReading;
  soilMoisture: SoilReading;
}

export interface FieldTask {
  id: string;
  title: string;
  description: string;
  urgency: string;
  icon: 'water' | 'search' | 'shield';
}

export interface FieldAlert {
  id: string;
  icon: 'sun' | 'wind' | 'rain';
  title: string;
  description: string;
}

export interface FieldHistoryEntry {
  id: string;
  date: string;
  statusLabel: string;
  statusColor: ConditionStatus;
  description: string;
  actionLabel: string;
}

export interface FieldDailyReport {
  headline: string;
  headlineLevel: ConditionStatus;
  headlineDescription: string;
  reasoning: string;
  tasks: FieldTask[];
  alerts: FieldAlert[];
  history: FieldHistoryEntry[];
  environment: FieldEnvironmentSnapshot;
  generatedAt: number;
}

function hashString(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = value.charCodeAt(i) + ((hash << 5) - hash);
  }
  return Math.abs(hash);
}

function dayBucket(): number {
  return Math.floor(Date.now() / (24 * 60 * 60 * 1000));
}

function soilReading(seed: number, base: number, spread: number, unit: string, decimals: number, goodText: string, cautionText: string): SoilReading {
  const value = Number((base + ((seed % (spread * 10)) / 10 - spread / 2)).toFixed(decimals));
  const status: ConditionStatus = seed % 5 === 0 ? 'caution' : 'good';
  return {
    value,
    unit,
    status,
    description: status === 'good' ? goodText : cautionText
  };
}

const WEEKDAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'];

export async function fetchFieldEnvironment(fieldId: string, cropName?: string | null): Promise<FieldDailyReport> {
  const seed = hashString(fieldId) + dayBucket();
  const crop = cropName || '작물';

  const currentTemp = 14 + (seed % 12);
  const maxTemp = currentTemp + 4 + (seed % 4);
  const minTemp = currentTemp - 8 - (seed % 3);
  const precipitationProbability = seed % 40;
  const precipitationAmount = precipitationProbability > 25 ? Number(((seed % 10) / 2).toFixed(1)) : 0;
  const humidity = 40 + (seed % 40);
  const windSpeed = Number((1 + (seed % 40) / 10).toFixed(1));
  const uvIndex = Number((2 + (seed % 60) / 10).toFixed(1));
  const sunshineHours = 3 + (seed % 7);

  const isHighTempDay = maxTemp >= 26;
  const isDryDay = precipitationAmount === 0 && humidity < 55;
  const hasCaution = isHighTempDay || isDryDay;

  const environment: FieldEnvironmentSnapshot = {
    currentTemp,
    maxTemp,
    minTemp,
    precipitationProbability,
    precipitationAmount,
    humidity,
    windSpeed,
    uvIndex,
    sunshineHours,
    soilPh: soilReading(seed, 6.5, 1.2, '', 1, `${crop} 재배에 적합한 산도예요.`, '산도가 다소 벗어나 있어요. 석회 시비를 고려해보세요.'),
    soilEc: soilReading(seed + 7, 1.0, 0.8, ' dS/m', 1, '염류 농도가 적정 범위예요.', '염류 농도가 다소 높아요. 관수량을 늘려보세요.'),
    soilTemp: soilReading(seed + 13, 17, 6, '°C', 0, `${crop} 생육에 알맞은 지온이에요.`, '지온이 낮아 생육이 더딜 수 있어요.'),
    soilMoisture: soilReading(seed + 19, 28, 12, '%', 0, '토양 수분이 적정 범위예요.', '토양이 건조해요. 관수가 필요할 수 있어요.')
  };

  const tasks: FieldTask[] = [
    {
      id: 'water',
      title: '흙 상태 확인 후 물주기 결정',
      description: precipitationAmount === 0
        ? '최근 비가 오지 않았어요. 흙을 만져보고 필요하면 물을 주세요.'
        : '최근 강수가 있었어요. 배수 상태만 가볍게 확인해주세요.',
      urgency: '오전 권장',
      icon: 'water'
    },
    {
      id: 'pest',
      title: '잎 뒷면 병해충 확인하기',
      description: humidity >= 60
        ? '현재 기온과 습도는 병해충이 발생하기 쉬운 환경이에요.'
        : '건조한 편이라 병해충 발생 가능성은 낮지만 한 번씩 확인해주세요.',
      urgency: '오전 권장',
      icon: 'search'
    },
    {
      id: 'support',
      title: '지지대 상태 확인하기',
      description: '줄기가 길어지고 있어 지지대 고정 상태를 확인해주세요.',
      urgency: '수시 확인',
      icon: 'shield'
    }
  ];

  const alerts: FieldAlert[] = [];
  if (isHighTempDay) {
    alerts.push({
      id: 'heat',
      icon: 'sun',
      title: '오후 고온 주의',
      description: `오늘 오후 최고기온이 ${maxTemp}℃까지 올라갈 예정이에요. 잎 처짐을 확인하고 필요 시 차광을 해주세요.`
    });
  }
  if (isDryDay) {
    alerts.push({
      id: 'dry',
      icon: 'wind',
      title: '건조 가능성',
      description: '최근 강수량이 적어 토양이 빠르게 마를 수 있어요. 흙 상태를 자주 확인해주세요.'
    });
  }
  if (precipitationProbability >= 60) {
    alerts.push({
      id: 'rain',
      icon: 'rain',
      title: '강수 가능성 높음',
      description: `오늘 강수확률이 ${precipitationProbability}%예요. 배수로 상태를 미리 점검해주세요.`
    });
  }

  const history: FieldHistoryEntry[] = Array.from({ length: 7 }, (_, i) => {
    const daySeed = hashString(fieldId) + dayBucket() - i;
    const date = new Date(Date.now() - i * 24 * 60 * 60 * 1000);
    const dayHot = 15 + (daySeed % 15) >= 25;
    const dayDry = daySeed % 4 === 0;
    const status: ConditionStatus = dayHot || dayDry ? 'caution' : (daySeed % 6 === 0 ? 'bad' : 'good');
    const statusLabel = status === 'good' ? '안정' : status === 'caution' ? '주의' : '확인 필요';
    const description = i === 0
      ? (hasCaution ? '오후 고온 주의 · 흙 상태 확인 완료' : '특이사항 없음')
      : status === 'good' ? '특이사항 없음'
        : status === 'caution' ? '낮 기온 높음 주의'
          : '잎 상태 확인 권장';
    const actionLabel = daySeed % 3 === 0 ? '물주기' : daySeed % 3 === 1 ? '비료' : '잎 상태 확인';
    return {
      id: `history-${i}`,
      date: `${date.getMonth() + 1}월 ${date.getDate()}일 (${WEEKDAY_LABELS[date.getDay()]})`,
      statusLabel,
      statusColor: status,
      description,
      actionLabel
    };
  });

  return {
    headline: hasCaution ? '오늘은 주의가 필요한 상태예요' : '오늘은 안정적인 상태예요',
    headlineLevel: hasCaution ? 'caution' : 'good',
    headlineDescription: isHighTempDay
      ? '낮 기온이 높아 토양과 잎 상태 확인이 필요해요.'
      : isDryDay
        ? '토양이 건조해질 수 있어 수분 상태 확인이 필요해요.'
        : `${crop}이(가) 안정적으로 자라고 있어요.`,
    reasoning: `최근 2일 강수량이 ${precipitationAmount}mm이고, 오늘 최고기온이 ${maxTemp}℃로 예상돼요. 환경(기온/습도/강수)에서 ${isHighTempDay ? '고온' : '건조'} 스트레스의 위험이 높아 흙 상태 확인 후 물주기를 결정하도록 안내했어요.`,
    tasks,
    alerts,
    history,
    environment,
    generatedAt: Date.now()
  };
}
