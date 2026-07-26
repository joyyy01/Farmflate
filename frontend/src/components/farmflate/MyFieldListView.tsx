import React from 'react';
import { motion } from 'framer-motion';
import { AlertCircle, AlertTriangle, Bot, ChevronRight } from 'lucide-react';
import type { TabState } from '../../types/farmflate';
import type { FieldProfile } from '../../types/report';
import { BottomNavigation } from '../common/BottomNavigation';
import { displayStage } from '../../constants/displayLabels';

const ALERT_SEVERITY_COLOR: Record<string, string> = { HIGH: '#DC2626', MEDIUM: '#D97706', LOW: '#8d9590' };
const MAX_VISIBLE_ALERTS = 2;

interface MyFieldListViewProps {
  fields: FieldProfile[];
  loadError?: string | null;
  onAddField: () => void;
  onSelectField: (field: FieldProfile) => void;
  onOpenAIChat: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
}

export const MyFieldListView: React.FC<MyFieldListViewProps> = ({
  fields = [],
  loadError,
  onAddField,
  onSelectField,
  onOpenAIChat,
  activeTab,
  onTabChange
}) => {
  const stageLabel = displayStage;

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar" style={{ padding: '32px 20px 96px 20px', overflowY: 'auto' }}>

        {/* Top Header */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#191F28', margin: 0 }}>
              마이 팜
            </h2>
          </div>
          <div style={{ fontSize: '0.78rem', color: '#8d9590', fontWeight: 600, marginTop: 6 }}>
            매일 아침 6시, 최신 날씨와 작물 상태로 자동 업데이트돼요
          </div>
        </div>

        {/* Dynamic Farm Cards List */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginBottom: 20 }}>
          {loadError && (
            <div role="alert" style={{ backgroundColor: '#FFF4F0', borderRadius: 20, padding: 20, border: '1px solid #FFD5C8', color: '#B54708', fontSize: '0.86rem', lineHeight: 1.6 }}>
              {loadError}
            </div>
          )}
          {!loadError && fields.length === 0 && (
            <div style={{ backgroundColor: '#F8FAF8', borderRadius: 20, padding: 20, border: '1px solid #E5E8EB', color: '#6F7772', fontSize: '0.86rem', lineHeight: 1.6 }}>
              등록된 농작물이 없습니다. 아래 버튼으로 농작물을 등록해 주세요.
            </div>
          )}
          {fields.map((field) => {
            const dailyStatus = field.dailyStatus ?? 'NEEDS_CHECK';
            const BADGE_STYLE: Record<string, { bg: string; color: string }> = {
              STABLE: { bg: '#E9F7EC', color: '#2E9F5B' },
              CAUTION: { bg: '#FEF3E2', color: '#D97706' },
              NEEDS_CHECK: { bg: '#F3F4F6', color: '#6F7772' }
            };
            const badgeBg = BADGE_STYLE[dailyStatus].bg;
            const badgeColor = BADGE_STYLE[dailyStatus].color;
            const cropName = field.cropName || '작물 정보 없음';
            const summary = field.dailyHeadline
              || field.latestReport?.headlineDescription
              || field.latestReport?.summary
              || field.suitabilityReport?.currentManagementPoints?.[0]
              || '내일 오전 6시에 첫 리포트가 만들어져요.';
            const alerts = field.dailyAlerts ?? [];
            const visibleAlerts = alerts.slice(0, MAX_VISIBLE_ALERTS);
            const hiddenAlertCount = alerts.length - visibleAlerts.length;
            // A1: 행동 유도형 배지 — 첫 번째 알림 제목을 우선 사용
            const actionBadge = alerts.length > 0
              ? alerts[0].title
              : field.dailyStatusLabel || '확인 필요';

            return (
              <div
                key={field.id}
                onClick={() => onSelectField(field)}
                role="button"
                tabIndex={0}
                onKeyDown={(e) => { if (e.key === 'Enter') onSelectField(field); }}
                style={{
                  backgroundColor: '#FFFFFF',
                  borderRadius: 20,
                  padding: 20,
                  border: '1px solid #E5E8EB',
                  cursor: 'pointer'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <img src="/svg-assets/crops/sprout.svg" alt="" aria-hidden="true" style={{ width: 44, height: 44, objectFit: 'contain' }} />
                    <div>
                      <h3 style={{ fontSize: '1.1rem', fontWeight: 900, color: '#191F28', margin: 0, marginBottom: 2 }}>
                        {field.fieldName}
                      </h3>
                      <div style={{ fontSize: '0.78rem', color: '#6F7772', fontWeight: 500 }}>
                        {cropName} · {stageLabel(field.stage)}{field.cultivationDay ? ` · 재배 ${field.cultivationDay}일차` : ''}
                      </div>
                    </div>
                  </div>
                  <span style={{
                    backgroundColor: badgeBg,
                    color: badgeColor,
                    padding: '4px 10px',
                    borderRadius: 12,
                    fontSize: '0.74rem',
                    fontWeight: 800
                  }}>
                    {actionBadge}
                  </span>
                </div>

                {visibleAlerts.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 20 }}>
                    <div style={{ fontSize: '0.74rem', fontWeight: 800, color: '#8d9590' }}>오늘의 주의·위험</div>
                    {visibleAlerts.map((alert) => (
                      <div
                        key={alert.key}
                        style={{
                          backgroundColor: '#F8FAF8', borderRadius: 14, padding: '10px 12px',
                          display: 'flex', alignItems: 'center', gap: 10
                        }}
                      >
                        <AlertTriangle size={17} color={ALERT_SEVERITY_COLOR[alert.severity] ?? '#D97706'} style={{ flexShrink: 0 }} />
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: '0.82rem', fontWeight: 800, color: '#191F28' }}>{alert.title}</div>
                          <div style={{ fontSize: '0.74rem', color: '#6F7772', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {alert.description}
                          </div>
                        </div>
                        <ChevronRight size={16} color="#B0B8B4" style={{ flexShrink: 0 }} />
                      </div>
                    ))}
                    {hiddenAlertCount > 0 && (
                      <div style={{ fontSize: '0.74rem', color: '#8d9590', fontWeight: 700, textAlign: 'right' }}>
                        +{hiddenAlertCount}개 더 확인하기
                      </div>
                    )}
                  </div>
                ) : (
                  <div style={{
                    backgroundColor: '#F8FAF8',
                    borderRadius: 14,
                    padding: '12px 14px',
                    fontSize: '0.82rem',
                    fontWeight: 700,
                    color: '#334155',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    marginBottom: 20
                  }}>
                    <AlertCircle size={16} color={badgeColor} /> {summary}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Dashed Add Button with Plus SVG */}
        <motion.button
          whileTap={{ scale: 0.98 }}
          className="btn-dashed-register"
          onClick={onAddField}
        >
          <img src="/svg-assets/ui-icons/plus.svg" alt="" style={{ width: 18, height: 18, color: '#2FA86A' }} />
          농작물 등록하기
        </motion.button>

      </div>

      {/* Floating AI Button */}
      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <Bot size={26} color="#FFFFFF" />
      </button>

      {/* 4-Tab Bottom Navigation */}
      <BottomNavigation
        activeTab={activeTab}
        onTabChange={onTabChange}
        onOpenAIChat={onOpenAIChat}
      />
    </div>
  );
};
