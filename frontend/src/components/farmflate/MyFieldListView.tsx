import React from 'react';
import { motion } from 'framer-motion';
import { ChevronRight } from 'lucide-react';
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
  fields: _fields,
  onAddField,
  onOpenAIChat,
  activeTab,
  onTabChange
}) => {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view" style={{ padding: '20px 20px 96px 20px' }}>
        
        {/* Top Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#191F28', margin: 0 }}>
            마이 팜
          </h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <button style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#1e2822', padding: 0, display: 'flex', alignItems: 'center' }}>
              <img src="/svg-assets/ui-icons/bell.svg" alt="알림" style={{ width: 22, height: 22 }} />
            </button>
            <div style={{
              width: 40, height: 40, borderRadius: '50%',
              backgroundColor: '#E9F7EC', border: '1.5px solid #2FA86A',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              overflow: 'hidden'
            }}>
              <img
                src="/svg-assets/brand/mascot/joy.svg"
                alt="마이 팜 프로필"
                style={{ width: 34, height: 34, objectFit: 'contain' }}
              />
            </div>
          </div>
        </div>

        {/* Card 1: 상추밭 */}
        <div style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 20,
          padding: 20,
          marginBottom: 16,
          border: '1px solid #E5E8EB',
          boxShadow: '0 4px 14px rgba(0, 0, 0, 0.03)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <img src="/svg-assets/crops/lettuce.svg" alt="상추" style={{ width: 44, height: 44, objectFit: 'contain' }} />
              <div>
                <h3 style={{ fontSize: '1.1rem', fontWeight: 900, color: '#191F28', margin: 0, marginBottom: 2 }}>
                  상추밭
                </h3>
                <div style={{ fontSize: '0.78rem', color: '#6F7772', fontWeight: 500 }}>
                  상추 · 재배 18일 차 · 생장 단계
                </div>
              </div>
            </div>
            <span style={{
              backgroundColor: '#FFF4DC',
              color: '#FF842F',
              padding: '4px 10px',
              borderRadius: 12,
              fontSize: '0.74rem',
              fontWeight: 800
            }}>
              물주기 필요
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
            <span style={{ color: '#FF842F' }}>📌</span> 오늘 물주기 필요
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

        {/* Card 2: 감자밭 */}
        <div style={{
          backgroundColor: '#FFFFFF',
          borderRadius: 20,
          padding: 20,
          marginBottom: 20,
          border: '1px solid #E5E8EB',
          boxShadow: '0 4px 14px rgba(0, 0, 0, 0.03)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <img src="/svg-assets/crops/potato.svg" alt="감자" style={{ width: 44, height: 44, objectFit: 'contain' }} />
              <div>
                <h3 style={{ fontSize: '1.1rem', fontWeight: 900, color: '#191F28', margin: 0, marginBottom: 2 }}>
                  감자밭
                </h3>
                <div style={{ fontSize: '0.78rem', color: '#6F7772', fontWeight: 500 }}>
                  감자 · 재배 32일 차 · 개화 단계
                </div>
              </div>
            </div>
            <span style={{
              backgroundColor: '#E0F2FE',
              color: '#0284C7',
              padding: '4px 10px',
              borderRadius: 12,
              fontSize: '0.74rem',
              fontWeight: 800
            }}>
              확인 필요
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
            <span style={{ color: '#0284C7' }}>📌</span> 서리 대비 덮개 점검 필요
          </div>

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
              <img src="/svg-assets/crops/sprout.svg" alt="새싹" style={{ width: 32, height: 32, objectFit: 'contain' }} />
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
          🌱 오늘 06:00 자동 분석됨
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
