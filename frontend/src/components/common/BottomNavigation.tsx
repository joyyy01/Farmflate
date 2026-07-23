import React from 'react';

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
        <img
          src={activeTab === 'home' ? '/svg-assets/ui-icons/nav-active/home.svg' : '/svg-assets/ui-icons/home.svg'}
          alt="홈"
          style={{ width: 22, height: 22, opacity: activeTab === 'home' ? 1 : 0.6 }}
        />
        <span>홈</span>
      </button>

      {/* 2. 마이 팜 (My Field) */}
      <button
        type="button"
        onClick={() => onTabChange('myfield')}
        className={`nav-item ${activeTab === 'myfield' ? 'active' : ''}`}
      >
        <img
          src={activeTab === 'myfield' ? '/svg-assets/ui-icons/nav-active/field.svg' : '/svg-assets/ui-icons/field.svg'}
          alt="마이 팜"
          style={{ width: 22, height: 22, opacity: activeTab === 'myfield' ? 1 : 0.6 }}
        />
        <span>마이 팜</span>
      </button>

      {/* 3. 커뮤니티 (Community) */}
      <button
        type="button"
        onClick={() => onTabChange('community')}
        className={`nav-item ${activeTab === 'community' ? 'active' : ''}`}
      >
        <img
          src={activeTab === 'community' ? '/svg-assets/ui-icons/nav-active/community.svg' : '/svg-assets/ui-icons/community.svg'}
          alt="커뮤니티"
          style={{ width: 22, height: 22, opacity: activeTab === 'community' ? 1 : 0.6 }}
        />
        <span>커뮤니티</span>
      </button>

      {/* 4. 설정 (Settings) */}
      <button
        type="button"
        onClick={() => onTabChange('settings')}
        className={`nav-item ${activeTab === 'settings' ? 'active' : ''}`}
      >
        <img
          src={activeTab === 'settings' ? '/svg-assets/ui-icons/nav-active/settings.svg' : '/svg-assets/ui-icons/settings.svg'}
          alt="설정"
          style={{ width: 22, height: 22, opacity: activeTab === 'settings' ? 1 : 0.6 }}
        />
        <span>설정</span>
      </button>
    </nav>
  );
};
