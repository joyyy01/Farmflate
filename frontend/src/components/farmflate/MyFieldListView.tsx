import React from 'react';
import { motion } from 'framer-motion';
import { AlertCircle, Bot } from 'lucide-react';
import type { TabState } from '../../types/farmflate';
import type { FieldProfile } from '../../types/report';
import { BottomNavigation } from '../common/BottomNavigation';

interface MyFieldListViewProps {
  fields: FieldProfile[];
  loadError?: string | null;
  onAddField: () => void;
  onOpenAIChat: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
}

export const MyFieldListView: React.FC<MyFieldListViewProps> = ({
  fields = [],
  loadError,
  onAddField,
  onOpenAIChat,
  activeTab,
  onTabChange
}) => {
  const stageLabel = (stage?: string | null) => {
    if (stage === 'before') return '심기 전';
    if (stage === 'growing') return '재배 중';
    return stage || '단계 정보 없음';
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar" style={{ padding: '32px 20px 96px 20px', overflowY: 'auto' }}>

        {/* Top Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#191F28', margin: 0 }}>
            마이 팜
          </h2>
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
            const hasSuitability = typeof field.suitabilityReport?.suitabilityScore === 'number' || Boolean(field.suitabilityReport?.grade);
            const badgeBg = hasSuitability ? '#E9F7EC' : '#F3F4F6';
            const badgeColor = hasSuitability ? '#2E9F5B' : '#6F7772';
            const cropName = field.cropName || '작물 정보 없음';
            const latestReport = field.latestReport;
            const summary = latestReport?.summary || field.suitabilityReport?.summary;
            const suitabilityLabel = field.suitabilityReport?.grade || (typeof field.suitabilityReport?.suitabilityScore === 'number' ? `적합도 ${field.suitabilityReport.suitabilityScore}점` : '적합도 자료 없음');

            return (
              <div
                key={field.id}
                style={{
                  backgroundColor: '#FFFFFF',
                  borderRadius: 20,
                  padding: 20,
                  border: '1px solid #E5E8EB'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <img src="/svg-assets/crops/sprout.svg" alt="" aria-hidden="true" style={{ width: 44, height: 44, objectFit: 'contain' }} />
                    <div>
                      <h3 style={{ fontSize: '1.1rem', fontWeight: 900, color: '#191F28', margin: 0, marginBottom: 2 }}>
                        {field.fieldName}
                      </h3>
                      <div style={{ fontSize: '0.78rem', color: '#6F7772', fontWeight: 500 }}>
                        {cropName} · {stageLabel(field.stage)}{field.cultivationStartDate ? ` · 시작 ${field.cultivationStartDate}` : ''}
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
                    {suitabilityLabel}
                  </span>
                </div>

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
                  <AlertCircle size={16} color={badgeColor} /> {summary || '서버에서 제공된 관리 요약이 아직 없습니다.'}
                </div>
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

        {fields.some(field => field.latestReport?.generatedAt) && (
          <div style={{ textAlign: 'center', marginTop: 14, fontSize: '0.74rem', color: '#8d9590', fontWeight: 600 }}>
            서버가 제공한 최신 리포트 기준
          </div>
        )}

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
