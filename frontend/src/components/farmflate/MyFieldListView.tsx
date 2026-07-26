import React from 'react';
import { motion } from 'framer-motion';
import { AlertCircle, AlertTriangle, Bot, ChevronRight } from 'lucide-react';
import type { TabState } from '../../types/farmflate';
import type { FieldProfile } from '../../types/report';
import { BottomNavigation } from '../common/BottomNavigation';
import { displayStage } from '../../constants/displayLabels';

const ALERT_SEVERITY_COLOR: Record<string, string> = { HIGH: '#DC2626', MEDIUM: '#D97706', LOW: '#8d9590' };
const MAX_VISIBLE_ALERTS = 2;
const CROP_ICON_BY_NAME: Record<string, string> = {
  '상추': '/svg-assets/crops/lettuce.svg',
  '오이': '/svg-assets/crops/cucumber.svg',
  '감자': '/svg-assets/crops/potato.svg',
  '고추': '/svg-assets/crops/pepper.svg',
  '토마토': '/svg-assets/crops/tomato.svg',
  '배추': '/svg-assets/crops/cabbage.svg',
  '사과': '/svg-assets/crops/apple.svg',
  '배': '/svg-assets/crops/pear.svg'
};

const STATUS_TONE: Record<string, 'stable' | 'caution' | 'check'> = {
  STABLE: 'stable',
  CAUTION: 'caution',
  NEEDS_CHECK: 'check'
};

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
      <div className="full-screen-view no-scrollbar farm-screen farm-screen--with-nav">

        {/* Top Header */}
        <header className="farm-page-header">
          <h2>마이 팜</h2>
          <p>
            매일 아침 6시, 최신 날씨와 작물 상태로 자동 업데이트돼요
          </p>
        </header>

        {/* Dynamic Farm Cards List */}
        <div className="farm-card-list">
          {loadError && (
            <div role="alert" className="farm-empty-state farm-empty-state--error">
              {loadError}
            </div>
          )}
          {!loadError && fields.length === 0 && (
            <div className="farm-empty-state">
              등록된 농작물이 없습니다. 아래 버튼으로 농작물을 등록해 주세요.
            </div>
          )}
          {fields.map((field) => {
            const dailyStatus = field.dailyStatus ?? 'NEEDS_CHECK';
            const statusTone = STATUS_TONE[dailyStatus] ?? 'check';
            const cropName = field.cropName || '작물 정보 없음';
            const cropIcon = CROP_ICON_BY_NAME[cropName] ?? '/svg-assets/crops/sprout.svg';
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
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onSelectField(field);
                  }
                }}
                className={`farm-card farm-card--${statusTone}`}
              >
                <div className="farm-card__heading">
                  <div className="farm-card__identity">
                    <span className="farm-card__crop-icon">
                      <img src={cropIcon} alt="" aria-hidden="true" />
                    </span>
                    <div className="farm-card__title-block">
                      <h3>
                        {field.fieldName}
                      </h3>
                      <p className="farm-card__meta">
                        {cropName} · {stageLabel(field.stage)}{field.cultivationDay ? ` · 재배 ${field.cultivationDay}일차` : ''}
                      </p>
                    </div>
                  </div>
                  <span className={`farm-status-chip farm-status-chip--${statusTone}`} title={actionBadge}>
                    {actionBadge}
                  </span>
                </div>

                {visibleAlerts.length > 0 ? (
                  <div className="farm-card__alerts">
                    <div className="farm-card__alert-label">오늘의 주의·위험</div>
                    {visibleAlerts.map((alert) => (
                      <div
                        key={alert.key}
                        className="farm-alert-row"
                      >
                        <AlertTriangle size={17} color={ALERT_SEVERITY_COLOR[alert.severity] ?? '#D97706'} style={{ flexShrink: 0 }} />
                        <div className="farm-alert-row__content">
                          <div className="farm-alert-row__title">{alert.title}</div>
                          <div className="farm-alert-row__description">
                            {alert.description}
                          </div>
                        </div>
                        <ChevronRight size={16} color="#B0B8B4" style={{ flexShrink: 0 }} />
                      </div>
                    ))}
                    {hiddenAlertCount > 0 && (
                      <div className="farm-card__more-alerts">
                        +{hiddenAlertCount}개 더 확인하기
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="farm-card__summary">
                    <AlertCircle size={16} className={`farm-card__summary-icon farm-card__summary-icon--${statusTone}`} />
                    <span>{summary}</span>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Dashed Add Button with Plus SVG */}
        <motion.button
          whileTap={{ scale: 0.98 }}
          className="btn-dashed-register farm-add-field"
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
