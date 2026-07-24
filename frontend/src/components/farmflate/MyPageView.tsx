import React from 'react';
import { ChevronRight, HelpCircle } from 'lucide-react';
import type { TabState } from '../../types/farmflate';
import { BottomNavigation } from '../common/BottomNavigation';

interface MyPageViewProps {
  userName?: string;
  userRegion?: string;
  onOpenAIChat: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
  onLogout?: () => void;
  onGoToExplore?: () => void;
}

export const MyPageView: React.FC<MyPageViewProps> = ({
  userName = '사용자님',
  userRegion = '전북 고창군',
  onOpenAIChat,
  activeTab,
  onTabChange,
  onLogout,
  onGoToExplore
}) => {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view" style={{ padding: '32px 20px 96px 20px' }}>
        
        {/* Top Header */}
        <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#191F28', marginBottom: 20 }}>
          설정
        </h2>

        {/* Profile Card with Pure Vector SVG Mascot */}
        <div style={{
          backgroundColor: '#EDF7ED',
          borderRadius: 20,
          padding: '20px',
          marginBottom: 24,
          border: '1px solid #D4EDDA',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 50, height: 50, borderRadius: '50%',
              backgroundColor: '#FFFFFF', border: '2px solid #2FA86A',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              overflow: 'hidden'
            }}>
              <img
                src="/svg-assets/brand/mascot/hello.svg"
                alt="사용자 프로필"
                style={{ width: 42, height: 42, objectFit: 'contain' }}
              />
            </div>
            <div>
              <h3 style={{ fontSize: '1.15rem', fontWeight: 900, color: '#191F28', margin: '0 0 2px 0' }}>
                {userName.endsWith('님') ? userName : `${userName}님`}
              </h3>
              <p style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 600, margin: 0 }}>
                {userRegion}
              </p>
            </div>
          </div>

          <img
            src="/svg-assets/brand/mascot/laugh.svg"
            alt="웃는 마스코트"
            style={{ width: 48, height: 48, objectFit: 'contain' }}
          />
        </div>

        {/* Category 1: 계정 */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: '0.82rem', fontWeight: 800, color: '#2e9f5b', marginBottom: 8, paddingLeft: 4 }}>
            계정
          </div>
          <div style={{ backgroundColor: '#FFFFFF', borderRadius: 18, border: '1px solid #E5E8EB', overflow: 'hidden' }}>
            <div style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/ui-icons/user.svg" alt="" style={{ width: 18, height: 18 }} /> 계정 정보
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>

            <div onClick={onGoToExplore} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/ui-icons/location.svg" alt="" style={{ width: 18, height: 18 }} /> 지역 수정 / 선택
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>

            <div style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/crops/leaf.svg" alt="" style={{ width: 18, height: 18 }} /> 관심 작물 설정
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>

            <div onClick={onLogout} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#ef4444' }}>
                <img src="/svg-assets/ui-icons/user.svg" alt="" style={{ width: 18, height: 18 }} /> 로그아웃
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>
          </div>
        </div>

        {/* Category 2: 내 활동 */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: '0.82rem', fontWeight: 800, color: '#2e9f5b', marginBottom: 8, paddingLeft: 4 }}>
            내 활동
          </div>
          <div style={{ backgroundColor: '#FFFFFF', borderRadius: 18, border: '1px solid #E5E8EB', overflow: 'hidden' }}>
            <div style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/ui-icons/star.svg" alt="" style={{ width: 18, height: 18 }} /> 저장한 게시글
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>

            <div style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/ui-icons/chat.svg" alt="" style={{ width: 18, height: 18 }} /> 작성한 게시글
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>
          </div>
        </div>

        {/* Category 3: 지원 */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: '0.82rem', fontWeight: 800, color: '#2e9f5b', marginBottom: 8, paddingLeft: 4 }}>
            지원
          </div>
          <div style={{ backgroundColor: '#FFFFFF', borderRadius: 18, border: '1px solid #E5E8EB', overflow: 'hidden' }}>
            <div style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <HelpCircle size={18} color="#7c847f" /> 고객센터
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>
          </div>
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
