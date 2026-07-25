import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  ArrowLeft, ChevronDown, ChevronRight, RefreshCw, Droplet, Search,
  Sun, Wind, CloudRain, AlertTriangle, Lightbulb, Bot
} from 'lucide-react';
import type { FieldProfile } from '../../types/report';
import { fetchFieldDailyReport, cropPhRangeText, type FieldDailyReport, type ConditionStatus } from '../../services/fieldEnvironmentService';
import {
  getFieldLogs, addFieldLog, getSoilTestResult, saveSoilTestResult,
  type FieldLogEntry, type FieldLogCategory, type SoilTestResult
} from '../../services/fieldLogService';

interface FieldDashboardViewProps {
  field: FieldProfile;
  onBack: () => void;
  onOpenAIChat: () => void;
}

const TASK_ICONS = { water: Droplet, search: Search };
const ALERT_ICONS = { sun: Sun, wind: Wind, rain: CloudRain };
const LOG_CATEGORIES: FieldLogCategory[] = ['물주기', '비료', '잎 상태 확인', '병해충 방제', '기타'];

const STATUS_STYLE: Record<ConditionStatus, { bg: string; color: string; border: string }> = {
  good: { bg: '#EDF7ED', color: '#2FA86A', border: '#D4EDDA' },
  caution: { bg: '#FEF7E8', color: '#D97706', border: '#FCE8C1' },
  bad: { bg: '#FDEDEC', color: '#DC2626', border: '#F6CFCB' }
};

const GLOSSARY: { key: string; title: string; body: string }[] = [
  { key: '토양 pH', title: '토양 pH가 뭐예요?', body: '흙이 산성인지 알칼리성인지를 나타내는 값이에요. 숫자가 7보다 낮으면 산성, 높으면 알칼리성이에요. 작물마다 잘 자라는 범위가 달라요.' },
  { key: 'EC(전기전도도)', title: 'EC가 뭐예요?', body: '흙 속에 녹아 있는 비료와 양분의 농도를 간접적으로 보여주는 값이에요. 너무 낮으면 양분이 부족할 수 있고, 너무 높으면 뿌리가 물을 흡수하기 어려울 수 있어요.' },
  { key: '강수량', title: '강수량이 뭐예요?', body: '실제로 내린 비의 양이에요. 보통 mm 단위로 표시해요.' },
  { key: '강수확률', title: '강수확률이 뭐예요?', body: '해당 시간이나 오늘 비가 올 가능성이에요. 강수확률이 높다고 반드시 많은 비가 내리는 것은 아니에요.' },
  { key: '습도', title: '습도가 뭐예요?', body: '공기 중에 수분이 얼마나 포함되어 있는지를 보여줘요. 습도가 너무 높고 기온도 높으면 일부 병해충이 발생하기 쉬워질 수 있어요.' },
  { key: '풍속', title: '풍속이 뭐예요?', body: '바람이 부는 세기예요. 바람이 강하면 어린 작물이나 지지대가 흔들릴 수 있어요.' },
  { key: '기타 용어', title: '기타 용어', body: '추가 용어 설명은 준비 중이에요.' }
];

const stageLabel = (stage?: string | null) => {
  if (stage === 'before') return '심기 전';
  if (stage === 'growing') return '생장기';
  return stage || '단계 정보 없음';
};

const formatAsOf = (timestamp: number) => {
  const date = new Date(timestamp);
  const isToday = new Date().toDateString() === date.toDateString();
  const time = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(date);
  return isToday ? `오늘 ${time}` : new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric' }).format(date) + ` ${time}`;
};

const formatLogDate = (iso: string) => {
  const date = new Date(iso);
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
  return `${date.getMonth() + 1}월 ${date.getDate()}일 (${weekdays[date.getDay()]})`;
};

const StatTile: React.FC<{ label: string; value: string; caption?: string }> = ({ label, value, caption }) => (
  <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 14, padding: '10px 8px', textAlign: 'center' }}>
    <div style={{ fontSize: '0.95rem', fontWeight: 900, color: '#191F28', marginBottom: 2, whiteSpace: 'nowrap' }}>{value}</div>
    <div style={{ fontSize: '0.66rem', color: '#8d9590', fontWeight: 600, marginBottom: caption ? 4 : 0 }}>{label}</div>
    {caption && <div style={{ fontSize: '0.62rem', color: '#9CA3AF', lineHeight: 1.4 }}>{caption}</div>}
  </div>
);

const LogHistory: React.FC<{ logs: FieldLogEntry[] }> = ({ logs }) => {
  if (logs.length === 0) {
    return (
      <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 16, padding: 20, textAlign: 'center', color: '#8d9590', fontSize: '0.82rem' }}>
        아직 기록이 없어요. "오늘의 기록 남기기"로 첫 기록을 남겨보세요.
      </div>
    );
  }
  return (
    <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, overflow: 'hidden' }}>
      {logs.slice(0, 7).map((log, idx, arr) => (
        <div
          key={log.id}
          style={{
            display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px',
            borderBottom: idx === arr.length - 1 ? 'none' : '1px solid #F1F5F9'
          }}
        >
          <span style={{ fontSize: '0.76rem', color: '#6F7772', width: 78, flexShrink: 0 }}>{formatLogDate(log.loggedAt)}</span>
          <span style={{ fontSize: '0.7rem', fontWeight: 800, color: '#2FA86A', backgroundColor: '#EDF7ED', padding: '3px 8px', borderRadius: 8, flexShrink: 0 }}>
            {log.category}
          </span>
          <span style={{ fontSize: '0.78rem', color: '#191F28', flex: 1, fontWeight: 500 }}>{log.note || '메모 없음'}</span>
        </div>
      ))}
    </div>
  );
};

export const FieldDashboardView: React.FC<FieldDashboardViewProps> = ({ field, onBack, onOpenAIChat }) => {
  const [activeSubTab, setActiveSubTab] = useState<'dashboard' | 'environment'>('dashboard');
  const [report, setReport] = useState<FieldDailyReport | null>(null);
  const [completedTaskIds, setCompletedTaskIds] = useState<string[]>([]);
  const [showReasoning, setShowReasoning] = useState(false);
  const [logs, setLogs] = useState<FieldLogEntry[]>(() => getFieldLogs(field.id));
  const [showLogForm, setShowLogForm] = useState(false);
  const [logCategory, setLogCategory] = useState<FieldLogCategory>('물주기');
  const [logNote, setLogNote] = useState('');
  const [soilTest, setSoilTest] = useState<SoilTestResult | null>(() => getSoilTestResult(field.id));
  const [showSoilForm, setShowSoilForm] = useState(false);
  const [soilPhInput, setSoilPhInput] = useState('');
  const [soilEcInput, setSoilEcInput] = useState('');
  const [expandedTerm, setExpandedTerm] = useState<string | null>(null);

  const loadReport = () => {
    void fetchFieldDailyReport(field.id, field.cropName).then(setReport);
  };

  useEffect(() => {
    loadReport();
    setLogs(getFieldLogs(field.id));
    setSoilTest(getSoilTestResult(field.id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [field.id, field.cropName]);

  const daysPlanted = field.cultivationStartDate
    ? Math.max(1, Math.floor((Date.now() - new Date(field.cultivationStartDate).getTime()) / (24 * 60 * 60 * 1000)) + 1)
    : null;

  const toggleTaskComplete = (taskId: string) => {
    setCompletedTaskIds(prev => prev.includes(taskId) ? prev.filter(id => id !== taskId) : [...prev, taskId]);
  };

  const submitLog = () => {
    if (!logNote.trim()) return;
    const next = addFieldLog(field.id, logCategory, logNote.trim());
    setLogs(next);
    setLogNote('');
    setShowLogForm(false);
  };

  const submitSoilTest = () => {
    const ph = soilPhInput.trim() ? Number(soilPhInput) : null;
    const ec = soilEcInput.trim() ? Number(soilEcInput) : null;
    if (ph === null && ec === null) return;
    const result: SoilTestResult = { ph, ec, testedAt: new Date().toISOString().slice(0, 10) };
    saveSoilTestResult(field.id, result);
    setSoilTest(result);
    setShowSoilForm(false);
  };

  if (!report) {
    return <div className="full-screen-view" style={{ padding: 20 }} />;
  }

  const activeTasks = report.tasks.filter(t => !completedTaskIds.includes(t.id));
  const completedTasks = report.tasks.filter(t => completedTaskIds.includes(t.id));
  const headlineStyle = STATUS_STYLE[report.headlineLevel];
  const phRangeText = cropPhRangeText(field.cropName);

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar" style={{ padding: '20px 20px 40px 20px', overflowY: 'auto' }}>

        {/* Back button */}
        <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, marginBottom: 16, display: 'flex' }}>
          <ArrowLeft size={22} color="#191F28" />
        </button>

        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 4 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <h2 style={{ fontSize: '1.35rem', fontWeight: 900, color: '#191F28', margin: 0 }}>{field.fieldName}</h2>
            <ChevronDown size={18} color="#8E9892" />
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '0.68rem', color: '#9CA3AF', marginBottom: 2 }}>마지막 업데이트</div>
            <button onClick={loadReport} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 4, padding: 0 }}>
              <span style={{ fontSize: '0.76rem', color: '#526157', fontWeight: 700 }}>{formatAsOf(report.generatedAt)}</span>
              <RefreshCw size={12} color="#526157" />
            </button>
          </div>
        </div>
        <div style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 600, marginBottom: 20 }}>
          {field.cropName || '작물 정보 없음'}
          {daysPlanted ? ` · 재배 ${daysPlanted}일차` : ''}
          {` · ${stageLabel(field.stage)}`}
        </div>

        {/* Tabs */}
        <div style={{ display: 'flex', backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 14, padding: 4, marginBottom: 20 }}>
          {(['dashboard', 'environment'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveSubTab(tab)}
              style={{
                flex: 1, border: 'none', cursor: 'pointer', padding: '10px 0', borderRadius: 10,
                fontSize: '0.86rem', fontWeight: 800,
                backgroundColor: activeSubTab === tab ? '#2FA86A' : '#FFFFFF',
                color: activeSubTab === tab ? '#FFFFFF' : '#526157'
              }}
            >
              {tab === 'dashboard' ? '대시보드' : '전체 환경 데이터'}
            </button>
          ))}
        </div>

        {activeSubTab === 'dashboard' ? (
          <>
            {/* 오늘 상태 */}
            <div style={{
              backgroundColor: headlineStyle.bg, border: `1px solid ${headlineStyle.border}`, borderRadius: 18,
              padding: '18px 18px', marginBottom: 24, display: 'flex', justifyContent: 'space-between', alignItems: 'center'
            }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                  {report.headlineLevel !== 'good' && <AlertTriangle size={18} color={headlineStyle.color} />}
                  <strong style={{ fontSize: '1.05rem', fontWeight: 900, color: '#191F28' }}>{report.headline}</strong>
                </div>
                <p style={{ margin: 0, fontSize: '0.82rem', color: '#526157', lineHeight: 1.5 }}>{report.headlineDescription}</p>
              </div>
              <Sun size={40} color="#F2B84B" style={{ flexShrink: 0, marginLeft: 12 }} />
            </div>

            {/* 오늘 꼭 해야 할 일 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: 0 }}>오늘 꼭 해야 할 일</h3>
              <span style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 700 }}>{completedTasks.length}/{report.tasks.length}</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 24 }}>
              {activeTasks.map(task => {
                const Icon = TASK_ICONS[task.icon];
                return (
                  <div key={task.id} style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, padding: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div style={{ width: 34, height: 34, borderRadius: 10, backgroundColor: '#EAF6EE', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          <Icon size={17} color="#2FA86A" />
                        </div>
                        <strong style={{ fontSize: '0.92rem', fontWeight: 800, color: '#191F28' }}>{task.title}</strong>
                      </div>
                      <span style={{ fontSize: '0.68rem', fontWeight: 800, color: '#D97706', backgroundColor: '#FEF7E8', padding: '3px 8px', borderRadius: 8, flexShrink: 0, marginLeft: 8 }}>
                        {task.urgency}
                      </span>
                    </div>
                    <p style={{ margin: '0 0 12px 44px', fontSize: '0.78rem', color: '#6F7772', lineHeight: 1.5 }}>{task.description}</p>
                    <div style={{ marginLeft: 44 }}>
                      <button
                        onClick={() => toggleTaskComplete(task.id)}
                        style={{ backgroundColor: '#2FA86A', color: '#FFFFFF', border: 'none', borderRadius: 10, padding: '8px 14px', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer' }}
                      >
                        확인했어요
                      </button>
                    </div>
                  </div>
                );
              })}
              {activeTasks.length === 0 && (
                <div style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '14px', fontSize: '0.82rem', color: '#8d9590', textAlign: 'center' }}>
                  오늘 할 일을 모두 확인했어요.
                </div>
              )}
            </div>

            {/* 오늘의 주의·위험 */}
            {report.alerts.length > 0 && (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: 0 }}>오늘의 주의·위험</h3>
                  <span style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 700 }}>{report.alerts.length}건</span>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 24 }}>
                  {report.alerts.map(alert => {
                    const Icon = ALERT_ICONS[alert.icon];
                    return (
                      <div
                        key={alert.id}
                        style={{
                          backgroundColor: '#FEFBF2', border: '1px solid #FCE8C1', borderRadius: 16, padding: '14px 16px',
                          display: 'flex', alignItems: 'flex-start', gap: 12
                        }}
                      >
                        <Icon size={20} color="#D97706" style={{ flexShrink: 0, marginTop: 2 }} />
                        <div style={{ flex: 1 }}>
                          <strong style={{ display: 'block', fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 3 }}>{alert.title}</strong>
                          <p style={{ margin: 0, fontSize: '0.78rem', color: '#6F7772', lineHeight: 1.5 }}>{alert.description}</p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </>
            )}

            {/* 오늘 재배 환경 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 4px' }}>오늘 재배 환경</h3>
            <p style={{ margin: '0 0 12px', fontSize: '0.76rem', color: '#8d9590' }}>오늘 작물이 자라는 데 영향을 주는 날씨를 한눈에 확인해보세요.</p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginBottom: 10 }}>
              <StatTile label="현재 기온" value={`${report.weather.currentTemp}℃`} caption="지금 작물이 느끼는 공기 온도예요." />
              <StatTile label="최고·최저 기온" value={`${report.weather.maxTemp}℃/${report.weather.minTemp}℃`} caption="오늘 예상되는 온도예요." />
              <StatTile label="강수확률" value={`${report.weather.precipitationProbability}%`} caption="오늘 비가 올 가능성이에요." />
              <StatTile label="습도" value={`${report.weather.humidity}%`} caption="공기 중 수분의 양이에요." />
            </div>
            <button
              onClick={() => setActiveSubTab('environment')}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#2FA86A', fontSize: '0.82rem', fontWeight: 800, display: 'block', margin: '0 auto 24px' }}
            >
              전체 환경 데이터 보기 ›
            </button>

            {/* 오늘의 기록 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>오늘의 기록</h3>
            <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 18, padding: 18, marginBottom: 24 }}>
              {!showLogForm ? (
                <>
                  <p style={{ margin: '0 0 14px', fontSize: '0.8rem', color: '#526157', lineHeight: 1.6 }}>
                    오늘 한 일을 기록해보세요.<br />물주기, 비료, 방제, 잎 상태 등을 기록하면 다음 업데이트에 반영돼요.
                  </p>
                  <motion.button
                    whileTap={{ scale: 0.98 }}
                    className="btn-farm-primary"
                    onClick={() => setShowLogForm(true)}
                    style={{ width: '100%', height: 48, fontSize: '0.9rem', borderRadius: 14 }}
                  >
                    + 오늘의 기록 남기기
                  </motion.button>
                </>
              ) : (
                <div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 10 }}>
                    {LOG_CATEGORIES.map(cat => (
                      <button
                        key={cat}
                        onClick={() => setLogCategory(cat)}
                        style={{
                          border: logCategory === cat ? 'none' : '1px solid #D1DFD7',
                          backgroundColor: logCategory === cat ? '#2FA86A' : '#FFFFFF',
                          color: logCategory === cat ? '#FFFFFF' : '#526157',
                          borderRadius: 10, padding: '6px 12px', fontSize: '0.78rem', fontWeight: 700, cursor: 'pointer'
                        }}
                      >
                        {cat}
                      </button>
                    ))}
                  </div>
                  <textarea
                    value={logNote}
                    onChange={(e) => setLogNote(e.target.value)}
                    placeholder="오늘 한 일을 적어주세요 (예: 오전에 물 흠뻑 줌)"
                    style={{ width: '100%', minHeight: 70, borderRadius: 12, border: '1px solid #D1DFD7', padding: 10, fontSize: '0.82rem', marginBottom: 10, resize: 'vertical', boxSizing: 'border-box' }}
                  />
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button onClick={submitLog} style={{ flex: 1, backgroundColor: '#2FA86A', color: '#FFFFFF', border: 'none', borderRadius: 12, padding: '10px 0', fontSize: '0.84rem', fontWeight: 800, cursor: 'pointer' }}>저장</button>
                    <button onClick={() => { setShowLogForm(false); setLogNote(''); }} style={{ flex: 1, backgroundColor: '#FFFFFF', color: '#6F7772', border: '1px solid #D1DFD7', borderRadius: 12, padding: '10px 0', fontSize: '0.84rem', fontWeight: 800, cursor: 'pointer' }}>취소</button>
                  </div>
                </div>
              )}
            </div>

            {/* 왜 이렇게 안내했나요? */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>왜 이렇게 안내했나요?</h3>
            <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 18, padding: 18, marginBottom: 24, display: 'flex', gap: 10 }}>
              <Lightbulb size={20} color="#2FA86A" style={{ flexShrink: 0, marginTop: 2 }} />
              <div style={{ flex: 1 }}>
                <p style={{ margin: '0 0 8px', fontSize: '0.8rem', color: '#526157', lineHeight: 1.6 }}>{report.reasoningSummary}</p>
                <button onClick={() => setShowReasoning(prev => !prev)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#2FA86A', fontSize: '0.8rem', fontWeight: 800, padding: 0 }}>
                  분석 근거 자세히 보기 {showReasoning ? '︿' : '›'}
                </button>
                {showReasoning && (
                  <ul style={{ margin: '10px 0 0', paddingLeft: 18, fontSize: '0.76rem', color: '#8d9590', lineHeight: 1.8 }}>
                    {report.reasoningPoints.map((point, i) => <li key={i}>{point}</li>)}
                  </ul>
                )}
              </div>
            </div>

            {/* 최근 7일 관리 이력 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: 0 }}>최근 7일 관리 이력</h3>
            </div>
            <LogHistory logs={logs} />
          </>
        ) : (
          <>
            {/* 오늘 재배 환경 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 4px' }}>오늘 재배 환경</h3>
            <p style={{ margin: '0 0 12px', fontSize: '0.76rem', color: '#8d9590' }}>작물이 오늘 어떤 환경에서 자라는지 날씨 데이터를 통해 확인해보세요.</p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 8 }}>
              <StatTile label="현재 기온" value={`${report.weather.currentTemp}℃`} />
              <StatTile label="최고·최저기온" value={`${report.weather.maxTemp}℃/${report.weather.minTemp}℃`} />
              <StatTile label="강수확률" value={`${report.weather.precipitationProbability}%`} />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 20 }}>
              <StatTile label="최근 강수량" value={`${report.weather.recentRainfallMm}mm`} caption="비가 적었다면 흙 상태를 더 자주 확인해야 해요." />
              <StatTile label="습도" value={`${report.weather.humidity}%`} caption="습도가 너무 높으면 병해충이 발생하기 쉬워요." />
              <StatTile label="풍속" value={`${report.weather.windSpeed} m/s`} caption="바람이 강하면 지지대가 흔들릴 수 있어요." />
            </div>

            {/* 내 토양 정보 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 4px' }}>내 토양 정보</h3>
            <p style={{ margin: '0 0 12px', fontSize: '0.76rem', color: '#8d9590' }}>토양검정이나 직접 입력한 내 밭의 토양 정보예요.</p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 12 }}>
              <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, padding: '14px 16px' }}>
                <div style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 700, marginBottom: 6 }}>토양 pH</div>
                {soilTest?.ph != null ? (
                  <>
                    <div style={{ fontSize: '1.3rem', fontWeight: 900, color: '#191F28', marginBottom: 4 }}>{soilTest.ph}</div>
                    <p style={{ margin: '0 0 6px', fontSize: '0.72rem', color: '#8d9590', lineHeight: 1.5 }}>{phRangeText || '토양검정 결과가 등록되어 있어요.'}</p>
                    <p style={{ margin: 0, fontSize: '0.68rem', color: '#9CA3AF' }}>최근 토양검정: {soilTest.testedAt}</p>
                  </>
                ) : (
                  <>
                    <div style={{ fontSize: '1rem', fontWeight: 800, color: '#9CA3AF', marginBottom: 4 }}>데이터 없음</div>
                    <p style={{ margin: 0, fontSize: '0.72rem', color: '#8d9590', lineHeight: 1.5 }}>토양검정 결과를 입력하면 내 밭에 맞는 설명을 받을 수 있어요.</p>
                  </>
                )}
              </div>
              <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, padding: '14px 16px' }}>
                <div style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 700, marginBottom: 6 }}>EC(전기전도도)</div>
                {soilTest?.ec != null ? (
                  <>
                    <div style={{ fontSize: '1.3rem', fontWeight: 900, color: '#191F28', marginBottom: 4 }}>{soilTest.ec}dS/m</div>
                    <p style={{ margin: '0 0 6px', fontSize: '0.72rem', color: '#8d9590', lineHeight: 1.5 }}>흙 속 양분의 농도가 등록되어 있어요.</p>
                    <p style={{ margin: 0, fontSize: '0.68rem', color: '#9CA3AF' }}>최근 측정일: {soilTest.testedAt}</p>
                  </>
                ) : (
                  <>
                    <div style={{ fontSize: '1rem', fontWeight: 800, color: '#9CA3AF', marginBottom: 4 }}>데이터 없음</div>
                    <p style={{ margin: 0, fontSize: '0.72rem', color: '#8d9590', lineHeight: 1.5 }}>EC는 흙 속 양분 농도를 보여주는 값이에요. 토양검정 결과를 입력해 주세요.</p>
                  </>
                )}
              </div>
            </div>

            {!showSoilForm ? (
              <button
                onClick={() => { setSoilPhInput(soilTest?.ph != null ? String(soilTest.ph) : ''); setSoilEcInput(soilTest?.ec != null ? String(soilTest.ec) : ''); setShowSoilForm(true); }}
                style={{ width: '100%', backgroundColor: '#FFFFFF', border: '1px solid #2FA86A', color: '#2FA86A', borderRadius: 14, padding: '12px 0', fontSize: '0.86rem', fontWeight: 800, cursor: 'pointer', marginBottom: 20 }}
              >
                토양검정 결과 입력·수정하기
              </button>
            ) : (
              <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 16, padding: 16, marginBottom: 20 }}>
                <label style={{ display: 'block', fontSize: '0.78rem', color: '#526157', fontWeight: 700, marginBottom: 6 }}>토양 pH</label>
                <input
                  type="number" step="0.1" value={soilPhInput} onChange={(e) => setSoilPhInput(e.target.value)}
                  placeholder="예: 6.4"
                  style={{ width: '100%', borderRadius: 10, border: '1px solid #D1DFD7', padding: '8px 10px', fontSize: '0.84rem', marginBottom: 12, boxSizing: 'border-box' }}
                />
                <label style={{ display: 'block', fontSize: '0.78rem', color: '#526157', fontWeight: 700, marginBottom: 6 }}>EC (dS/m)</label>
                <input
                  type="number" step="0.1" value={soilEcInput} onChange={(e) => setSoilEcInput(e.target.value)}
                  placeholder="예: 1.1"
                  style={{ width: '100%', borderRadius: 10, border: '1px solid #D1DFD7', padding: '8px 10px', fontSize: '0.84rem', marginBottom: 12, boxSizing: 'border-box' }}
                />
                <div style={{ display: 'flex', gap: 8 }}>
                  <button onClick={submitSoilTest} style={{ flex: 1, backgroundColor: '#2FA86A', color: '#FFFFFF', border: 'none', borderRadius: 12, padding: '10px 0', fontSize: '0.84rem', fontWeight: 800, cursor: 'pointer' }}>저장</button>
                  <button onClick={() => setShowSoilForm(false)} style={{ flex: 1, backgroundColor: '#FFFFFF', color: '#6F7772', border: '1px solid #D1DFD7', borderRadius: 12, padding: '10px 0', fontSize: '0.84rem', fontWeight: 800, cursor: 'pointer' }}>취소</button>
                </div>
              </div>
            )}

            {/* 용어 쉽게 설명 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 4px' }}>용어가 어려우신가요?</h3>
            <p style={{ margin: '0 0 12px', fontSize: '0.76rem', color: '#8d9590' }}>처음 보는 농업 용어를 쉬운 말로 설명해드려요.</p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 20 }}>
              {GLOSSARY.map(term => (
                <div key={term.key} style={{ width: '100%' }}>
                  <button
                    onClick={() => setExpandedTerm(prev => prev === term.key ? null : term.key)}
                    style={{
                      width: '100%', textAlign: 'left', border: '1px solid #E5E8EB',
                      backgroundColor: expandedTerm === term.key ? '#EDF7ED' : '#FFFFFF',
                      borderRadius: 12, padding: '10px 14px', fontSize: '0.82rem', fontWeight: 700, color: '#191F28',
                      cursor: 'pointer', display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: expandedTerm === term.key ? 0 : 6
                    }}
                  >
                    {term.key}
                    <ChevronRight size={14} style={{ transform: expandedTerm === term.key ? 'rotate(90deg)' : 'none' }} color="#8E9892" />
                  </button>
                  {expandedTerm === term.key && (
                    <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderTop: 'none', borderRadius: '0 0 12px 12px', padding: '12px 14px', marginBottom: 6 }}>
                      <strong style={{ display: 'block', fontSize: '0.8rem', color: '#191F28', marginBottom: 4 }}>{term.title}</strong>
                      <p style={{ margin: 0, fontSize: '0.76rem', color: '#6F7772', lineHeight: 1.6 }}>{term.body}</p>
                      {term.key === '토양 pH' && phRangeText && (
                        <p style={{ margin: '6px 0 0', fontSize: '0.76rem', color: '#2FA86A', fontWeight: 700 }}>{phRangeText}</p>
                      )}
                      {term.key === 'EC(전기전도도)' && (
                        <p style={{ margin: '6px 0 0', fontSize: '0.76rem', color: soilTest?.ec != null ? '#2FA86A' : '#8d9590', fontWeight: 700 }}>
                          {soilTest?.ec != null ? `현재 EC는 ${field.cropName || '작물'} 생육에 참고할 수 있는 값이에요.` : '현재 밭의 EC 데이터는 아직 등록되지 않았어요.'}
                        </p>
                      )}
                    </div>
                  )}
                </div>
              ))}
            </div>

            {/* 데이터 출처 및 기준일 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>데이터 출처 및 기준일</h3>
            <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 16, padding: '14px 16px', marginBottom: 20 }}>
              <p style={{ margin: '0 0 6px', fontSize: '0.78rem', color: '#6F7772', lineHeight: 1.8 }}>
                날씨: {report.weather.source} · 기준 {formatAsOf(report.weather.asOf)}
              </p>
              <p style={{ margin: 0, fontSize: '0.78rem', color: '#6F7772', lineHeight: 1.8 }}>
                토양: {soilTest ? `사용자 입력 · ${soilTest.testedAt}` : '등록된 토양검정 결과 없음'}
              </p>
            </div>

            {/* 최근 7일 환경 요약 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: 0 }}>최근 7일 환경 요약</h3>
            </div>
            <LogHistory logs={logs} />
          </>
        )}
      </div>

      {/* Floating AI Button */}
      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <Bot size={26} color="#FFFFFF" />
      </button>
    </div>
  );
};
