import React from 'react';
import { Home, Sprout, MessageSquare, User } from 'lucide-react';
import type { TabState } from '../../types/farmflate';

interface BottomNavigationProps {
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
  onOpenAIChat: () => void;
}

export const BottomNavigation: React.FC<BottomNavigationProps> = ({
  activeTab,
  onTabChange
}) => {
  return (
    <nav className="bottom-nav">
      {/* 1. 홈 (Home) */}
      <button
        type="button"
        onClick={() => onTabChange('home')}
        className={`nav-item ${activeTab === 'home' ? 'active' : ''}`}
      >
        <div style={{ width: 24, height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Home size={22} color={activeTab === 'home' ? '#2FA86A' : '#8E9892'} strokeWidth={activeTab === 'home' ? 2.5 : 2} />
        </div>
        <span>홈</span>
      </button>

      {/* 2. 마이 팜 (My Field) */}
      <button
        type="button"
        onClick={() => onTabChange('myfield')}
        className={`nav-item ${activeTab === 'myfield' ? 'active' : ''}`}
      >
        <div style={{ width: 24, height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Sprout size={22} color={activeTab === 'myfield' ? '#2FA86A' : '#8E9892'} strokeWidth={activeTab === 'myfield' ? 2.5 : 2} />
        </div>
        <span>마이 팜</span>
      </button>

      {/* 3. 커뮤니티 (Community) */}
      <button
        type="button"
        onClick={() => onTabChange('community')}
        className={`nav-item ${activeTab === 'community' ? 'active' : ''}`}
      >
        <div style={{ width: 24, height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <MessageSquare size={22} color={activeTab === 'community' ? '#2FA86A' : '#8E9892'} strokeWidth={activeTab === 'community' ? 2.5 : 2} />
        </div>
        <span>커뮤니티</span>
      </button>

      {/* 4. 마이 (Settings / MyPage) */}
      <button
        type="button"
        onClick={() => onTabChange('settings')}
        className={`nav-item ${activeTab === 'settings' ? 'active' : ''}`}
      >
        <div style={{ width: 24, height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <User size={22} color={activeTab === 'settings' ? '#2FA86A' : '#8E9892'} strokeWidth={activeTab === 'settings' ? 2.5 : 2} />
        </div>
        <span>마이</span>
      </button>
    </nav>
  );
};
