import React from 'react';
import { motion } from 'framer-motion';
import { ChevronRight, AlertCircle } from 'lucide-react';
import type { MyFieldItem, TabState } from '../../types/farmflate';
import { BottomNavigation } from '../common/BottomNavigation';

interface MyFieldListViewProps {
  fields: MyFieldItem[];
  onAddField: () => void;
  onOpenAIChat: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
}

export const MyFieldListView: React.FC<MyFieldListViewProps> = ({
  fields = [],
  onAddField,
  onOpenAIChat,
  activeTab,
  onTabChange
}) => {
  const getCropIcon = (cropName: string) => {
    if (cropName.includes('상추')) return '/svg-assets/crops/lettuce.svg';
    if (cropName.includes('감자')) return '/svg-assets/crops/potato.svg';
    if (cropName.includes('오이')) return '/svg-assets/crops/cucumber.svg';
    if (cropName.includes('사과')) return '/svg-assets/crops/apple.svg';
    if (cropName.includes('배')) return '/svg-assets/crops/pear.svg';
    return '/svg-assets/crops/sprout.svg';
  };

  const displayFields: MyFieldItem[] = fields.length > 0 ? fields : [
    {
      id: 'field_1',
      fieldName: '상추밭',
      cropName: '상추',
      daysPlanted: 18,
      stage: '생장 단계',
      statusBadge: '물주기 필요',
      statusBadgeColor: 'yellow',
      todayTask: '오늘 물주기 필요',
      reportTime: '오늘 06:00 자동 분석됨'
    },
    {
      id: 'field_2',
      fieldName: '감자밭',
      cropName: '감자',
      daysPlanted: 32,
      stage: '개화 단계',
      statusBadge: '확인 필요',
      statusBadgeColor: 'blue',
      todayTask: '서리 대비 덮개 점검 필요',
      reportTime: '오늘 06:00 자동 분석됨'
    }
  ];

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
          {displayFields.map((field) => {
            const isYellow = field.statusBadgeColor === 'yellow';
            const badgeBg = isYellow ? '#FFF4DC' : '#E0F2FE';
            const badgeColor = isYellow ? '#FF842F' : '#0284C7';
            const icon = getCropIcon(field.cropName);

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
                    <img src={icon} alt={field.cropName} style={{ width: 44, height: 44, objectFit: 'contain' }} />
                    <div>
                      <h3 style={{ fontSize: '1.1rem', fontWeight: 900, color: '#191F28', margin: 0, marginBottom: 2 }}>
                        {field.fieldName}
                      </h3>
                      <div style={{ fontSize: '0.78rem', color: '#6F7772', fontWeight: 500 }}>
                        {field.cropName} · 재배 {field.daysPlanted}일 차 · {field.stage}
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
                    {field.statusBadge}
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
                  marginBottom: 12
                }}>
                  <AlertCircle size={16} color={badgeColor} /> {field.todayTask}
                </div>

                {/* Today's Report Banner */}
                <div style={{
                  backgroundColor: '#EDF7ED',
                  borderRadius: 16,
                  padding: '12px 16px',
                  border: '1px solid #D4EDDA',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <img src="/svg-assets/weather/water-drop-cheer.svg" alt="물방울" style={{ width: 34, height: 38, objectFit: 'contain' }} />
                    <div>
                      <div style={{ fontSize: '0.88rem', fontWeight: 800, color: '#145238' }}>
                        오늘의 리포트가 준비됐어요!
                      </div>
                      <div style={{ fontSize: '0.76rem', color: '#2e9f5b', fontWeight: 500 }}>
                        지금 바로 확인해보세요!
                      </div>
                    </div>
                  </div>
                  <ChevronRight size={18} color="#145238" />
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

        <div style={{ textAlign: 'center', marginTop: 14, fontSize: '0.74rem', color: '#8d9590', fontWeight: 600 }}>
          오늘 06:00 자동 분석됨
        </div>

      </div>

      {/* Floating AI Button */}
      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <img src="/svg-assets/ui-icons/ai-chat.svg" alt="AI 채팅" style={{ width: 26, height: 26, filter: 'brightness(0) invert(1)' }} />
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
