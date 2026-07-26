import React, { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import {
  ArrowLeft, ChevronDown, ChevronRight, RefreshCw, Droplet, Search,
  AlertTriangle, Lightbulb, Bot, Shield, ClipboardList
} from 'lucide-react';
import type { FieldProfile } from '../../types/report';
import type { FieldActivityLog, FieldDashboardResponse, FieldLogCategory } from '../../types/report';
import { ApiService, ApiError } from '../../services/api';
import { displayStage, FIELD_STATUS_LABELS, LOG_CATEGORY_LABELS } from '../../constants/displayLabels';

interface FieldDashboardViewProps {
  field: FieldProfile;
  onBack: () => void;
  onOpenAIChat: () => void;
  onDateChange?: (date: string | null) => void;
}

const LOG_CATEGORIES: FieldLogCategory[] = ['WATERING', 'FERTILIZING', 'LEAF_CHECK', 'PEST_CONTROL', 'OTHER'];

const STATUS_STYLE: Record<FieldDashboardResponse['report']['status'], { bg: string; color: string; border: string }> = {
  STABLE: { bg: '#EDF7ED', color: '#2FA86A', border: '#D4EDDA' },
  CAUTION: { bg: '#FEF7E8', color: '#D97706', border: '#FCE8C1' },
  NEEDS_CHECK: { bg: '#FDEDEC', color: '#DC2626', border: '#F6CFCB' }
};

const SEVERITY_COLOR: Record<string, string> = { HIGH: '#DC2626', MEDIUM: '#D97706', LOW: '#8d9590' };

const ZONE_COLOR: Record<string, string> = { '적정': '#2FA86A', '주의': '#D97706', '위험': '#DC2626', '확인 필요': '#8d9590' };

/** Task key → the log category it most naturally corresponds to, so "기록 남기기" can preselect it. */
const TASK_LOG_CATEGORY: Record<string, FieldLogCategory> = {
  CHECK_SOIL_MOISTURE: 'WATERING',
  CHECK_LEAF_CONDITION: 'LEAF_CHECK',
  CHECK_DRAINAGE: 'OTHER',
  CHECK_SUPPORT_STAKES: 'OTHER',
  CHECK_FIELD_DIRECTLY: 'OTHER'
};

/** Task key → icon component (B7: icon by task type, not badge). */
const TASK_ICON: Record<string, React.FC<{ size?: number; color?: string }>> = {
  CHECK_SOIL_MOISTURE: Droplet,
  CHECK_LEAF_CONDITION: Search,
  CHECK_DRAINAGE: Droplet,
  CHECK_SUPPORT_STAKES: Shield,
  CHECK_FIELD_DIRECTLY: Search
};

const StatusGauge: React.FC<{ score: number | null; zone: string }> = ({ score, zone }) => {
  const color = ZONE_COLOR[zone] ?? '#8d9590';
  const pct = score == null ? null : Math.max(0, Math.min(100, score));
  return (
    <div>
      <div style={{
        position: 'relative', height: 10, borderRadius: 6,
        background: 'linear-gradient(to right, #2FA86A 0%, #2FA86A 30%, #D97706 30%, #D97706 65%, #DC2626 65%, #DC2626 100%)'
      }}>
        {pct != null && (
          <>
            {/* 말풍선 툴팁 */}
            <div style={{
              position: 'absolute', bottom: '100%', left: `calc(${pct}% - 16px)`, marginBottom: 6,
              backgroundColor: '#191F28', color: '#FFFFFF', fontSize: '0.72rem', fontWeight: 900,
              padding: '3px 8px', borderRadius: 8, whiteSpace: 'nowrap', textAlign: 'center', minWidth: 32
            }}>
              {score}
              <div style={{
                position: 'absolute', top: '100%', left: '50%', transform: 'translateX(-50%)',
                width: 0, height: 0, borderLeft: '5px solid transparent', borderRight: '5px solid transparent',
                borderTop: '5px solid #191F28'
              }} />
            </div>
            <div style={{
              position: 'absolute', top: -3, left: `calc(${pct}% - 8px)`, width: 16, height: 16,
              borderRadius: '50%', background: '#FFFFFF', border: `3px solid ${color}`, boxShadow: '0 1px 3px rgba(0,0,0,0.25)'
            }} />
          </>
        )}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6, fontSize: '0.66rem', color: '#9CA3AF', fontWeight: 700 }}>
        <span>적정</span><span>주의</span><span>위험</span>
      </div>
    </div>
  );
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

const formatKoreanDate = (iso: string) => {
  const date = new Date(iso + 'T00:00:00');
  const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
  return `${date.getMonth() + 1}월 ${date.getDate()}일 (${weekdays[date.getDay()]})`;
};

const formatAsOf = (iso: string) => {
  if (!iso) return '';
  const date = new Date(iso);
  const isToday = new Date().toDateString() === date.toDateString();
  const time = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(date);
  return isToday ? `오늘 ${time}` : new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric' }).format(date) + ` ${time}`;
};

const displayMetric = (value: number | null | undefined, suffix: string): string =>
  value == null ? '데이터 없음' : `${value}${suffix}`;

const historyThStyle: React.CSSProperties = {
  textAlign: 'left', padding: '10px 12px', fontSize: '0.7rem', fontWeight: 800, color: '#8d9590', whiteSpace: 'nowrap'
};
const historyTdStyle: React.CSSProperties = {
  padding: '10px 12px', fontSize: '0.76rem', color: '#191F28', verticalAlign: 'middle'
};

const StatTile: React.FC<{ label: string; value: string; caption?: string }> = ({ label, value, caption }) => (
  <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 14, padding: '10px 8px', textAlign: 'center' }}>
    <div style={{ fontSize: '0.95rem', fontWeight: 900, color: '#191F28', marginBottom: 2, whiteSpace: 'nowrap' }}>{value}</div>
    <div style={{ fontSize: '0.66rem', color: '#8d9590', fontWeight: 600, marginBottom: caption ? 4 : 0 }}>{label}</div>
    {caption && <div style={{ fontSize: '0.62rem', color: '#9CA3AF', lineHeight: 1.4 }}>{caption}</div>}
  </div>
);

const LogHistory: React.FC<{ logs: FieldActivityLog[] }> = ({ logs }) => {
  if (logs.length === 0) {
    return (
      <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 16, padding: 20, textAlign: 'center', color: '#8d9590', fontSize: '0.82rem' }}>
        아직 오늘 기록이 없어요. 아래에서 첫 기록을 남겨보세요.
      </div>
    );
  }
  return (
    <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, overflow: 'hidden' }}>
      {logs.map((log, idx, arr) => (
        <div
          key={log.id}
          style={{
            display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px',
            borderBottom: idx === arr.length - 1 ? 'none' : '1px solid #F1F5F9'
          }}
        >
          <span style={{ fontSize: '0.7rem', fontWeight: 800, color: '#2FA86A', backgroundColor: '#EDF7ED', padding: '3px 8px', borderRadius: 8, flexShrink: 0 }}>
            {log.categoryLabel}
          </span>
          <span style={{ fontSize: '0.78rem', color: '#191F28', flex: 1, fontWeight: 500 }}>{log.note || '메모 없음'}</span>
        </div>
      ))}
    </div>
  );
};

export const FieldDashboardView: React.FC<FieldDashboardViewProps> = ({ field, onBack, onOpenAIChat, onDateChange }) => {
  const [activeSubTab, setActiveSubTab] = useState<'dashboard' | 'environment'>('dashboard');
  const [selectedDate, setSelectedDate] = useState<string | undefined>();
  const [dashboard, setDashboard] = useState<FieldDashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [acknowledgingTaskKey, setAcknowledgingTaskKey] = useState<string | null>(null);
  const [showReasoning, setShowReasoning] = useState(false);
  const [showLogForm, setShowLogForm] = useState(false);
  const [logCategory, setLogCategory] = useState<FieldLogCategory>('WATERING');
  const [logNote, setLogNote] = useState('');
  const [submittingLog, setSubmittingLog] = useState(false);
  const [logError, setLogError] = useState<string | null>(null);
  const [expandedTerm, setExpandedTerm] = useState<string | null>(null);
  const logSectionRef = useRef<HTMLHeadingElement>(null);

  const startLogForTask = (taskKey: string) => {
    setLogCategory(TASK_LOG_CATEGORY[taskKey] ?? 'OTHER');
    setShowLogForm(true);
    requestAnimationFrame(() => logSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  };

  const reload = () => {
    let current = true;
    setLoading(true);
    setError(null);

    ApiService.getFieldDashboard(field.id, selectedDate)
      .then(data => { if (current) setDashboard(data); })
      .catch(err => {
        if (!current) return;
        setError(err instanceof ApiError ? err.message : '밭 정보를 불러오지 못했습니다.');
      })
      .finally(() => { if (current) setLoading(false); });

    return () => { current = false; };
  };

  useEffect(() => {
    const cleanup = reload();
    return cleanup;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [field.id, selectedDate]);

  useEffect(() => {
    onDateChange?.(dashboard?.report.reportDate ?? null);
  }, [dashboard?.report.reportDate, onDateChange]);

  const acknowledgeTask = async (taskKey: string) => {
    if (!dashboard || dashboard.report.historical || acknowledgingTaskKey) return;
    const previous = dashboard;
    setAcknowledgingTaskKey(taskKey);
    setDashboard(current => current ? { ...current, tasks: current.tasks.filter(task => task.key !== taskKey) } : current);

    try {
      await ApiService.acknowledgeFieldTask(field.id, dashboard.report.reportDate, taskKey);
    } catch (err) {
      setDashboard(previous);
      setError(err instanceof ApiError ? err.message : '할 일 확인을 저장하지 못했습니다.');
    } finally {
      setAcknowledgingTaskKey(null);
    }
  };

  const submitLog = async () => {
    if (submittingLog || dashboard?.report.historical) return;
    setSubmittingLog(true);
    setLogError(null);
    try {
      const created = await ApiService.createFieldLog(
        field.id,
        { category: logCategory, note: logNote.trim() },
        crypto.randomUUID()
      );
      setDashboard(current => current ? { ...current, todayLogs: [created, ...current.todayLogs] } : current);
      setLogNote('');
      setShowLogForm(false);
    } catch (err) {
      setLogError(err instanceof ApiError ? err.message : '기록을 저장하지 못했습니다.');
    } finally {
      setSubmittingLog(false);
    }
  };

  if (loading) {
    return <div className="full-screen-view" style={{ padding: 20, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#8d9590' }}>불러오는 중...</div>;
  }
  if (error || !dashboard) {
    return (
      <div className="full-screen-view" style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, display: 'flex', alignSelf: 'flex-start' }}>
          <ArrowLeft size={22} color="#191F28" />
        </button>
        <div role="alert" style={{ backgroundColor: '#FFF4F2', border: '1px solid #F3CCC5', borderRadius: 14, padding: 20, color: '#A43A2F', fontSize: '0.86rem' }}>
          {error || '표시할 밭 정보가 없습니다.'}
        </div>
        <button onClick={reload} style={{ alignSelf: 'flex-start', border: 'none', background: 'none', color: '#2FA86A', fontWeight: 800, cursor: 'pointer' }}>다시 시도</button>
      </div>
    );
  }

  const headlineStyle = STATUS_STYLE[dashboard.report.status] ?? STATUS_STYLE.NEEDS_CHECK;
  const allAcknowledged = dashboard.report.taskCountBeforeAcknowledgement > 0 && dashboard.tasks.length === 0;

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar" style={{ padding: '20px 20px 40px 20px', overflowY: 'auto' }}>

        <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, marginBottom: 16, display: 'flex' }}>
          <ArrowLeft size={22} color="#191F28" />
        </button>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 4 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
            <h2 style={{ fontSize: '1.35rem', fontWeight: 900, color: '#191F28', margin: 0 }}>{dashboard.field.fieldName}</h2>
            <ChevronDown size={18} color="#8E9892" />
          </div>
          {!dashboard.report.historical && (
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.68rem', color: '#9CA3AF', marginBottom: 2 }}>마지막 업데이트</div>
              <button onClick={reload} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 4, padding: 0 }}>
                <span style={{ fontSize: '0.76rem', color: '#526157', fontWeight: 700 }}>{formatAsOf(dashboard.report.generatedAt)}</span>
                <RefreshCw size={12} color="#526157" />
              </button>
            </div>
          )}
        </div>
        <div style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 600, marginBottom: 12 }}>
          {dashboard.field.cropName || '작물 정보 없음'}
          {dashboard.field.cultivationDay ? ` · 재배 ${dashboard.field.cultivationDay}일차` : ''}
          {` · ${displayStage(dashboard.field.stage)}`}
          {` · ${dashboard.field.regionName}`}
        </div>

        {dashboard.report.historical && (
          <div style={{ backgroundColor: '#F1F5F9', borderRadius: 14, padding: '10px 16px', marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.82rem', fontWeight: 700, color: '#3D4A5C' }}>
              {formatKoreanDate(dashboard.report.reportDate)} 기록
            </span>
            <button onClick={() => setSelectedDate(undefined)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#2FA86A', fontSize: '0.8rem', fontWeight: 800 }}>
              오늘로 돌아가기
            </button>
          </div>
        )}

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
            {/* 0+1. 오늘 상태 + 종합 상태 점수 (통합 카드) */}
            <div style={{
              backgroundColor: headlineStyle.bg, border: `1px solid ${headlineStyle.border}`, borderRadius: 18,
              padding: '18px 18px', marginBottom: 24
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                {dashboard.report.status !== 'STABLE' && <AlertTriangle size={18} color={headlineStyle.color} />}
                <strong style={{ fontSize: '1.05rem', fontWeight: 900, color: '#191F28' }}>{dashboard.report.headline}</strong>
              </div>
              <p style={{ margin: '0 0 14px', fontSize: '0.82rem', color: '#526157', lineHeight: 1.5 }}>{dashboard.report.headlineDescription}</p>
              <StatusGauge score={dashboard.report.statusScore} zone={dashboard.report.statusScoreZone} />
              <span style={{ display: 'inline-block', marginTop: 10, fontSize: '0.7rem', fontWeight: 800, color: headlineStyle.color, backgroundColor: '#FFFFFF', padding: '3px 10px', borderRadius: 8 }}>
                {FIELD_STATUS_LABELS[dashboard.report.status] ?? '확인 필요'}
              </span>
            </div>

            {/* 2. 오늘 꼭 해야 할 일 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>오늘 꼭 해야 할 일</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 24 }}>
              {dashboard.tasks.map(task => {
                const Icon = TASK_ICON[task.key] ?? Search;
                return (
                  <div key={task.key} style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, padding: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div style={{ width: 34, height: 34, borderRadius: 10, backgroundColor: '#EAF6EE', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                          <Icon size={17} color="#2FA86A" />
                        </div>
                        <strong style={{ fontSize: '0.92rem', fontWeight: 800, color: '#191F28' }}>{task.title}</strong>
                      </div>
                      <span style={{ fontSize: '0.68rem', fontWeight: 800, color: '#D97706', backgroundColor: '#FEF7E8', padding: '3px 8px', borderRadius: 8, flexShrink: 0, marginLeft: 8 }}>
                        {task.badge === 'MORNING_RECOMMENDED' ? '오전 권장' : '수시 확인'}
                      </span>
                    </div>
                    <p style={{ margin: '0 0 12px 44px', fontSize: '0.78rem', color: '#6F7772', lineHeight: 1.5 }}>{task.description}</p>
                    {!dashboard.report.historical && (
                      <div style={{ marginLeft: 44, display: 'flex', gap: 8 }}>
                        <button
                          onClick={() => acknowledgeTask(task.key)}
                          disabled={acknowledgingTaskKey === task.key}
                          style={{ backgroundColor: '#2FA86A', color: '#FFFFFF', border: 'none', borderRadius: 10, padding: '8px 14px', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer', opacity: acknowledgingTaskKey === task.key ? 0.6 : 1 }}
                        >
                          확인했어요
                        </button>
                        {task.badge === 'MORNING_RECOMMENDED' && (
                          <button
                            onClick={() => startLogForTask(task.key)}
                            style={{ backgroundColor: '#FFFFFF', color: '#2FA86A', border: '1px solid #BFE3CD', borderRadius: 10, padding: '8px 14px', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer' }}
                          >
                            기록 남기기
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
              {dashboard.tasks.length === 0 && (
                <div style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '14px', fontSize: '0.82rem', color: '#8d9590', textAlign: 'center' }}>
                  {allAcknowledged ? '오늘 할 일을 모두 확인했어요.' : '오늘 추가로 안내할 관리 작업이 없어요.'}
                </div>
              )}
            </div>

            {/* 3. 왜 이렇게 안내했나요? */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '0 0 12px' }}>
              <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: 0 }}>왜 이렇게 안내했나요?</h3>
              {dashboard.reasoning.points.length > 0 && (
                <button onClick={() => setShowReasoning(prev => !prev)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#2FA86A', fontSize: '0.78rem', fontWeight: 800, padding: 0 }}>
                  분석 근거 자세히 보기 {showReasoning ? '︿' : '›'}
                </button>
              )}
            </div>
            <div style={{ backgroundColor: '#EAF7EE', borderRadius: 18, padding: 18, marginBottom: 24, display: 'flex', gap: 10 }}>
              <Lightbulb size={20} color="#2FA86A" style={{ flexShrink: 0, marginTop: 2 }} />
              <div style={{ flex: 1 }}>
                <p style={{ margin: 0, fontSize: '0.8rem', color: '#526157', lineHeight: 1.6 }}>{dashboard.reasoning.summary}</p>
                {showReasoning && (
                  <ul style={{ margin: '10px 0 0', paddingLeft: 18, fontSize: '0.76rem', color: '#8d9590', lineHeight: 1.8 }}>
                    {dashboard.reasoning.points.map((point, i) => <li key={i}>{point}</li>)}
                  </ul>
                )}
              </div>
            </div>

            {/* 4. 오늘의 주의·위험 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>오늘의 주의·위험</h3>
            {dashboard.alerts.length === 0 ? (
              <div style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '14px', fontSize: '0.82rem', color: '#8d9590', textAlign: 'center', marginBottom: 24 }}>
                오늘 예보에서 특별한 주의·위험이 확인되지 않았어요.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 24 }}>
                {dashboard.alerts.map(alert => (
                  <div
                    key={alert.key}
                    style={{ backgroundColor: '#FEFBF2', border: '1px solid #FCE8C1', borderRadius: 16, padding: '14px 16px', display: 'flex', alignItems: 'flex-start', gap: 12 }}
                  >
                    <AlertTriangle size={20} color={SEVERITY_COLOR[alert.severity] ?? '#D97706'} style={{ flexShrink: 0, marginTop: 2 }} />
                    <div style={{ flex: 1 }}>
                      <strong style={{ display: 'block', fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 3 }}>{alert.title}</strong>
                      <p style={{ margin: 0, fontSize: '0.78rem', color: '#6F7772', lineHeight: 1.5 }}>{alert.description}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* 5. 오늘의 기록 */}
            <h3 ref={logSectionRef} style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>오늘의 기록</h3>
            {!dashboard.report.historical && (
              <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 18, padding: 18, marginBottom: 16 }}>
                {!showLogForm ? (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                    <div style={{ width: 42, height: 42, borderRadius: 12, backgroundColor: '#EAF6EE', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      <ClipboardList size={20} color="#2FA86A" />
                    </div>
                    <div style={{ flex: 1 }}>
                      <strong style={{ display: 'block', fontSize: '0.9rem', fontWeight: 800, color: '#191F28', marginBottom: 2 }}>오늘의 기록</strong>
                      <span style={{ fontSize: '0.76rem', color: '#6F7772', lineHeight: 1.5 }}>물주기, 비료, 방제, 잎 상태 등을 기록하면 다음 안내에 반영돼요.</span>
                    </div>
                    <motion.button
                      whileTap={{ scale: 0.98 }}
                      onClick={() => setShowLogForm(true)}
                      style={{ backgroundColor: '#2FA86A', color: '#FFFFFF', border: 'none', borderRadius: 20, padding: '10px 16px', fontSize: '0.8rem', fontWeight: 800, cursor: 'pointer', whiteSpace: 'nowrap', flexShrink: 0 }}
                    >
                      + 기록 남기기
                    </motion.button>
                  </div>
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
                          {LOG_CATEGORY_LABELS[cat]}
                        </button>
                      ))}
                    </div>
                    <textarea
                      value={logNote}
                      onChange={(e) => setLogNote(e.target.value)}
                      placeholder="메모를 남겨보세요 (선택)"
                      disabled={submittingLog}
                      style={{ width: '100%', minHeight: 70, borderRadius: 12, border: '1px solid #D1DFD7', padding: 10, fontSize: '0.82rem', marginBottom: 10, resize: 'vertical', boxSizing: 'border-box' }}
                    />
                    {logError && <p style={{ margin: '0 0 10px', fontSize: '0.76rem', color: '#DC2626' }}>{logError}</p>}
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button onClick={submitLog} disabled={submittingLog} style={{ flex: 1, backgroundColor: '#2FA86A', color: '#FFFFFF', border: 'none', borderRadius: 12, padding: '10px 0', fontSize: '0.84rem', fontWeight: 800, cursor: 'pointer', opacity: submittingLog ? 0.7 : 1 }}>저장</button>
                      <button onClick={() => { setShowLogForm(false); setLogNote(''); setLogError(null); }} disabled={submittingLog} style={{ flex: 1, backgroundColor: '#FFFFFF', color: '#6F7772', border: '1px solid #D1DFD7', borderRadius: 12, padding: '10px 0', fontSize: '0.84rem', fontWeight: 800, cursor: 'pointer' }}>취소</button>
                    </div>
                  </div>
                )}
              </div>
            )}
            <div style={{ marginBottom: 24 }}>
              <LogHistory logs={dashboard.todayLogs} />
            </div>

            {/* 6. 최근 7일 관리 이력 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>최근 7일 관리 이력</h3>
            <div style={{ overflowX: 'auto', border: '1px solid #E5E8EB', borderRadius: 14 }}>
              <table style={{ width: '100%', minWidth: 480, borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ backgroundColor: '#F8FAF8' }}>
                    <th style={historyThStyle}>날짜</th>
                    <th style={historyThStyle}>상태</th>
                    <th style={historyThStyle}>관리내용</th>
                    <th style={historyThStyle}>주요지표</th>
                    <th style={historyThStyle}>상세확인</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.history.map((item, idx) => {
                    const rowStatusStyle = STATUS_STYLE[item.status ?? 'NEEDS_CHECK'] ?? STATUS_STYLE.NEEDS_CHECK;
                    return (
                    <tr key={item.date} style={{ borderTop: idx === 0 ? 'none' : '1px solid #F1F5F9', backgroundColor: selectedDate === item.date ? '#F8FAF8' : '#FFFFFF' }}>
                      <td style={historyTdStyle}>
                        <time dateTime={item.date} style={{ fontWeight: 700, color: '#526157', whiteSpace: 'nowrap' }}>{formatKoreanDate(item.date)}</time>
                      </td>
                      <td style={historyTdStyle}>
                        <span style={{ fontSize: '0.7rem', fontWeight: 800, color: rowStatusStyle.color, backgroundColor: rowStatusStyle.bg, padding: '3px 8px', borderRadius: 8, whiteSpace: 'nowrap' }}>
                          {item.statusLabel}
                        </span>
                      </td>
                      <td style={{ ...historyTdStyle, color: '#526157' }}>{item.managementSummary || item.logLabels.join(' · ') || '-'}</td>
                      <td style={{ ...historyTdStyle, color: '#8d9590', whiteSpace: 'nowrap' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                          {item.keyMetric && <Droplet size={12} color="#8d9590" style={{ flexShrink: 0 }} />}
                          {item.keyMetric ?? '-'}
                        </div>
                      </td>
                      <td style={historyTdStyle}>
                        <button
                          disabled={!item.reportAvailable}
                          onClick={() => { setSelectedDate(item.date); setActiveSubTab('dashboard'); }}
                          aria-current={selectedDate === item.date ? 'date' : undefined}
                          style={{
                            border: 'none', background: 'none', color: item.reportAvailable ? '#2FA86A' : '#C4C9C4',
                            fontSize: '0.76rem', fontWeight: 800, cursor: item.reportAvailable ? 'pointer' : 'default',
                            padding: 0, textDecoration: 'underline', textUnderlineOffset: 2
                          }}
                        >
                          상세확인
                        </button>
                      </td>
                    </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        ) : (
          <>
            {/* 오늘 날씨 데이터 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 4px' }}>오늘 날씨 데이터</h3>
            <p style={{ margin: '0 0 12px', fontSize: '0.76rem', color: '#8d9590' }}>작물이 오늘 어떤 환경에서 자라는지 날씨 데이터를 통해 확인해보세요.</p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8, marginBottom: 8 }}>
              <StatTile label="현재 기온" value={displayMetric(dashboard.weather.currentTemperature, '℃')} />
              <StatTile label="최고/최저 기온" value={
                dashboard.weather.maxTemperature == null && dashboard.weather.minTemperature == null
                  ? '데이터 없음'
                  : `${dashboard.weather.maxTemperature ?? '-'}℃ / ${dashboard.weather.minTemperature ?? '-'}℃`
              } />
              <StatTile label="강수확률" value={displayMetric(dashboard.weather.precipitationProbability, '%')} />
              <StatTile label="강수량" value={displayMetric(dashboard.weather.rainfallMm, 'mm')} />
              <StatTile label="습도" value={displayMetric(dashboard.weather.humidity, '%')} caption="습도가 너무 높으면 병해충이 발생하기 쉬워요." />
              <StatTile label="풍속" value={displayMetric(dashboard.weather.windSpeed, 'm/s')} caption="바람이 강하면 지지대가 흔들릴 수 있어요." />
            </div>

            {/* 내 토양 정보 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '20px 0 4px' }}>내 토양 정보</h3>
            <p style={{ margin: '0 0 12px', fontSize: '0.76rem', color: '#8d9590' }}>연결된 지역 분석의 흙토람 토양검정 평균값이에요. 필지 실측값은 아니에요.</p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 20 }}>
              <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, padding: '14px 16px' }}>
                <div style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 700, marginBottom: 6 }}>토양 pH</div>
                <div style={{ fontSize: '1rem', fontWeight: 800, color: dashboard.soil?.ph != null ? '#191F28' : '#9CA3AF' }}>
                  {dashboard.soil?.ph != null ? dashboard.soil.ph.toFixed(1) : '데이터 없음'}
                </div>
              </div>
              <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, padding: '14px 16px' }}>
                <div style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 700, marginBottom: 6 }}>EC(전기전도도)</div>
                <div style={{ fontSize: '1rem', fontWeight: 800, color: dashboard.soil?.ec != null ? '#191F28' : '#9CA3AF' }}>
                  {dashboard.soil?.ec != null ? `${dashboard.soil.ec.toFixed(2)} dS/m` : '데이터 없음'}
                </div>
              </div>
            </div>

            {/* 용어 쉽게 설명 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 4px' }}>용어가 어려우신가요?</h3>
            <p style={{ margin: '0 0 12px', fontSize: '0.76rem', color: '#8d9590' }}>처음 보는 농업 용어를 쉬운 말로 설명해드려요.</p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
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
                    </div>
                  )}
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <Bot size={26} color="#FFFFFF" />
      </button>
    </div>
  );
};
