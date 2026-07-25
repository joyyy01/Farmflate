import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import {
  ArrowLeft, ChevronDown, ChevronRight, RefreshCw, Droplet, Search, Shield,
  Sun, Wind, CloudRain, AlertTriangle, Lightbulb, FileEdit, Bot
} from 'lucide-react';
import type { FieldProfile } from '../../types/report';
import { fetchFieldEnvironment, type FieldDailyReport, type ConditionStatus, type SoilReading } from '../../services/fieldEnvironmentService';

interface FieldDashboardViewProps {
  field: FieldProfile;
  onBack: () => void;
  onOpenAIChat: () => void;
}

const TASK_ICONS = { water: Droplet, search: Search, shield: Shield };
const ALERT_ICONS = { sun: Sun, wind: Wind, rain: CloudRain };

const STATUS_STYLE: Record<ConditionStatus, { bg: string; color: string; border: string }> = {
  good: { bg: '#EDF7ED', color: '#2FA86A', border: '#D4EDDA' },
  caution: { bg: '#FEF7E8', color: '#D97706', border: '#FCE8C1' },
  bad: { bg: '#FDEDEC', color: '#DC2626', border: '#F6CFCB' }
};

const stageLabel = (stage?: string | null) => {
  if (stage === 'before') return '심기 전';
  if (stage === 'growing') return '생장기';
  return stage || '단계 정보 없음';
};

const formatUpdatedAt = (timestamp: number) => {
  const date = new Date(timestamp);
  const isToday = new Date().toDateString() === date.toDateString();
  const time = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(date);
  return isToday ? `오늘 ${time}` : new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric' }).format(date) + ` ${time}`;
};

const SoilStat: React.FC<{ label: string; reading: SoilReading }> = ({ label, reading }) => {
  const style = STATUS_STYLE[reading.status];
  return (
    <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, padding: '14px 16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
        <span style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 700 }}>{label}</span>
        <span style={{ fontSize: '0.72rem', fontWeight: 800, color: style.color, backgroundColor: style.bg, padding: '2px 8px', borderRadius: 8 }}>
          {reading.status === 'good' ? '양호' : reading.status === 'caution' ? '주의' : '확인 필요'}
        </span>
      </div>
      <div style={{ fontSize: '1.3rem', fontWeight: 900, color: '#191F28', marginBottom: 4 }}>
        {reading.value}{reading.unit}
      </div>
      <p style={{ margin: 0, fontSize: '0.74rem', color: '#8d9590', lineHeight: 1.5 }}>{reading.description}</p>
    </div>
  );
};

const HistoryTable: React.FC<{ history: FieldDailyReport['history'] }> = ({ history }) => (
  <div style={{ backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 16, overflow: 'hidden' }}>
    {history.map((entry, idx) => {
      const style = STATUS_STYLE[entry.statusColor];
      return (
        <div
          key={entry.id}
          style={{
            display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px',
            borderBottom: idx === history.length - 1 ? 'none' : '1px solid #F1F5F9'
          }}
        >
          <span style={{ fontSize: '0.76rem', color: '#6F7772', width: 78, flexShrink: 0 }}>{entry.date}</span>
          <span style={{
            fontSize: '0.7rem', fontWeight: 800, color: style.color, backgroundColor: style.bg,
            padding: '3px 8px', borderRadius: 8, flexShrink: 0
          }}>
            {entry.statusLabel}
          </span>
          <span style={{ fontSize: '0.78rem', color: '#191F28', flex: 1, fontWeight: 500 }}>{entry.description}</span>
          <span style={{ fontSize: '0.74rem', color: '#526157', fontWeight: 700, flexShrink: 0 }}>{entry.actionLabel}</span>
        </div>
      );
    })}
  </div>
);

export const FieldDashboardView: React.FC<FieldDashboardViewProps> = ({ field, onBack, onOpenAIChat }) => {
  const [activeSubTab, setActiveSubTab] = useState<'dashboard' | 'environment'>('dashboard');
  const [report, setReport] = useState<FieldDailyReport | null>(null);
  const [completedTaskIds, setCompletedTaskIds] = useState<string[]>([]);
  const [showCompleted, setShowCompleted] = useState(false);
  const [showReasoning, setShowReasoning] = useState(false);

  const loadReport = () => {
    void fetchFieldEnvironment(field.id, field.cropName).then(setReport);
  };

  useEffect(() => {
    loadReport();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [field.id, field.cropName]);

  const daysPlanted = field.cultivationStartDate
    ? Math.max(1, Math.floor((Date.now() - new Date(field.cultivationStartDate).getTime()) / (24 * 60 * 60 * 1000)) + 1)
    : null;

  const toggleTaskComplete = (taskId: string) => {
    setCompletedTaskIds(prev => prev.includes(taskId) ? prev.filter(id => id !== taskId) : [...prev, taskId]);
  };

  if (!report) {
    return <div className="full-screen-view" style={{ padding: 20 }} />;
  }

  const activeTasks = report.tasks.filter(t => !completedTaskIds.includes(t.id));
  const completedTasks = report.tasks.filter(t => completedTaskIds.includes(t.id));
  const headlineStyle = STATUS_STYLE[report.headlineLevel];

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
              <span style={{ fontSize: '0.76rem', color: '#526157', fontWeight: 700 }}>{formatUpdatedAt(report.generatedAt)}</span>
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
        <div style={{ display: 'flex', backgroundColor: '#F1F5F1', borderRadius: 14, padding: 4, marginBottom: 20 }}>
          {(['dashboard', 'environment'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveSubTab(tab)}
              style={{
                flex: 1, border: 'none', cursor: 'pointer', padding: '10px 0', borderRadius: 10,
                fontSize: '0.86rem', fontWeight: 800,
                backgroundColor: activeSubTab === tab ? '#2FA86A' : 'transparent',
                color: activeSubTab === tab ? '#FFFFFF' : '#6F7772'
              }}
            >
              {tab === 'dashboard' ? '대시보드' : '전체 환경 데이터'}
            </button>
          ))}
        </div>

        {activeSubTab === 'dashboard' ? (
          <>
            {/* Headline */}
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

            {/* Tasks */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: 0 }}>오늘 꼭 해야 할 일</h3>
              <span style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 700 }}>{completedTasks.length}/{report.tasks.length}</span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 8 }}>
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
                    <div style={{ display: 'flex', gap: 8, marginLeft: 44 }}>
                      <button
                        onClick={() => toggleTaskComplete(task.id)}
                        style={{ backgroundColor: '#2FA86A', color: '#FFFFFF', border: 'none', borderRadius: 10, padding: '8px 14px', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer' }}
                      >
                        확인했어요
                      </button>
                      <button
                        style={{ backgroundColor: '#FFFFFF', color: '#2FA86A', border: '1px solid #BFE3CB', borderRadius: 10, padding: '8px 14px', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer' }}
                      >
                        기록 남기기
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
            {completedTasks.length > 0 && (
              <div style={{ marginBottom: 24 }}>
                <button
                  onClick={() => setShowCompleted(prev => !prev)}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#2FA86A', fontSize: '0.82rem', fontWeight: 800, display: 'flex', alignItems: 'center', gap: 4, margin: '4px auto 0' }}
                >
                  완료한 일 보기 <ChevronDown size={14} style={{ transform: showCompleted ? 'rotate(180deg)' : 'none' }} />
                </button>
                {showCompleted && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 10 }}>
                    {completedTasks.map(task => (
                      <div key={task.id} style={{ backgroundColor: '#F8FAF8', borderRadius: 14, padding: '10px 14px', fontSize: '0.8rem', color: '#8d9590', textDecoration: 'line-through' }}>
                        {task.title}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Alerts */}
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
                      <button
                        key={alert.id}
                        style={{
                          backgroundColor: '#FEFBF2', border: '1px solid #FCE8C1', borderRadius: 16, padding: '14px 16px',
                          display: 'flex', alignItems: 'flex-start', gap: 12, textAlign: 'left', cursor: 'pointer'
                        }}
                      >
                        <Icon size={20} color="#D97706" style={{ flexShrink: 0, marginTop: 2 }} />
                        <div style={{ flex: 1 }}>
                          <strong style={{ display: 'block', fontSize: '0.88rem', fontWeight: 800, color: '#191F28', marginBottom: 3 }}>{alert.title}</strong>
                          <p style={{ margin: 0, fontSize: '0.78rem', color: '#6F7772', lineHeight: 1.5 }}>{alert.description}</p>
                        </div>
                        <ChevronRight size={16} color="#D97706" style={{ flexShrink: 0, marginTop: 3 }} />
                      </button>
                    );
                  })}
                </div>
              </>
            )}

            {/* Environment summary */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>오늘 농사 환경 요약</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginBottom: 10 }}>
              {[
                { label: '현재 기온', value: `${report.environment.currentTemp}℃` },
                { label: '최고/최저 기온', value: `${report.environment.maxTemp}℃ / ${report.environment.minTemp}℃` },
                { label: '강수확률', value: `${report.environment.precipitationProbability}%` },
                { label: '습도', value: `${report.environment.humidity}%` }
              ].map(stat => (
                <div key={stat.label} style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 14, padding: '10px 6px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.95rem', fontWeight: 900, color: '#191F28', marginBottom: 2, whiteSpace: 'nowrap' }}>{stat.value}</div>
                  <div style={{ fontSize: '0.66rem', color: '#8d9590', fontWeight: 600 }}>{stat.label}</div>
                </div>
              ))}
            </div>
            <button
              onClick={() => setActiveSubTab('environment')}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#2FA86A', fontSize: '0.82rem', fontWeight: 800, display: 'block', margin: '0 auto 24px' }}
            >
              전체 환경 데이터 보기 ›
            </button>

            {/* Record CTA */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>오늘의 기록</h3>
            <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 18, padding: 18, marginBottom: 24 }}>
              <div style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
                <FileEdit size={20} color="#2FA86A" style={{ flexShrink: 0, marginTop: 2 }} />
                <p style={{ margin: 0, fontSize: '0.8rem', color: '#526157', lineHeight: 1.6 }}>
                  오늘 한 일을 기록해보세요.<br />물주기, 비료, 방제, 잎 상태 등을 기록하면 다음 업데이트에 반영돼요.
                </p>
              </div>
              <motion.button
                whileTap={{ scale: 0.98 }}
                className="btn-farm-primary"
                style={{ width: '100%', height: 48, fontSize: '0.9rem', borderRadius: 14 }}
              >
                + 오늘의 기록 남기기
              </motion.button>
            </div>

            {/* Reasoning */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>왜 이렇게 안내했나요?</h3>
            <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 18, padding: 18, marginBottom: 24, display: 'flex', gap: 10 }}>
              <Lightbulb size={20} color="#2FA86A" style={{ flexShrink: 0, marginTop: 2 }} />
              <div>
                <p style={{ margin: '0 0 8px', fontSize: '0.8rem', color: '#526157', lineHeight: 1.6 }}>{report.reasoning}</p>
                <button onClick={() => setShowReasoning(prev => !prev)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#2FA86A', fontSize: '0.8rem', fontWeight: 800, padding: 0 }}>
                  분석 근거 자세히 보기 ›
                </button>
                {showReasoning && (
                  <p style={{ margin: '10px 0 0', fontSize: '0.76rem', color: '#8d9590', lineHeight: 1.6 }}>
                    적합도 점수, 토양·기후 조건, 최근 리스크 이력을 종합해 매일 오전 6시에 다시 계산돼요.
                  </p>
                )}
              </div>
            </div>
          </>
        ) : (
          <>
            {/* 기상 정보 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 4px' }}>기상 정보</h3>
            <p style={{ margin: '0 0 12px', fontSize: '0.76rem', color: '#8d9590' }}>현재 기준 실시간 데이터예요.</p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginBottom: 20 }}>
              {[
                { label: '기온', value: `${report.environment.currentTemp}℃` },
                { label: '최고/최저기온', value: `${report.environment.maxTemp}℃ / ${report.environment.minTemp}℃` },
                { label: '강수확률', value: `${report.environment.precipitationProbability}%` },
                { label: '강수량', value: `${report.environment.precipitationAmount}mm` },
                { label: '습도', value: `${report.environment.humidity}%` },
                { label: '풍속', value: `${report.environment.windSpeed} m/s` },
                { label: '자외선', value: `${report.environment.uvIndex} MJ/㎡` },
                { label: '일조', value: `${report.environment.sunshineHours}h` }
              ].map(stat => (
                <div key={stat.label} style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 14, padding: '10px 4px', textAlign: 'center' }}>
                  <div style={{ fontSize: '0.86rem', fontWeight: 900, color: '#191F28', marginBottom: 2, whiteSpace: 'nowrap' }}>{stat.value}</div>
                  <div style={{ fontSize: '0.64rem', color: '#8d9590', fontWeight: 600 }}>{stat.label}</div>
                </div>
              ))}
            </div>

            {/* 토양 정보 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>토양 정보</h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 20 }}>
              <SoilStat label="토양 pH" reading={report.environment.soilPh} />
              <SoilStat label="EC (전기전도도)" reading={report.environment.soilEc} />
              <SoilStat label="토양 온도" reading={report.environment.soilTemp} />
              <SoilStat label="토양 수분" reading={report.environment.soilMoisture} />
            </div>

            {/* 데이터 정보 */}
            <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: '0 0 12px' }}>데이터 정보</h3>
            <div style={{ backgroundColor: '#F8FAF8', border: '1px solid #E5E8EB', borderRadius: 16, padding: '14px 16px', marginBottom: 20 }}>
              <p style={{ margin: 0, fontSize: '0.78rem', color: '#6F7772', lineHeight: 1.8 }}>
                기상 정보: NASA POWER, 기상청 종관기상관측(ASOS)<br />
                토양 정보: 국립농업과학원 흙토람 토양환경지도
              </p>
            </div>

            {/* History */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <h3 style={{ fontSize: '1.02rem', fontWeight: 900, color: '#191F28', margin: 0 }}>최근 7일 환경 이력</h3>
              <span style={{ fontSize: '0.8rem', color: '#2FA86A', fontWeight: 700 }}>더보기 ›</span>
            </div>
            <HistoryTable history={report.history} />
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
