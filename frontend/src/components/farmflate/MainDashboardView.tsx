import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronRight } from 'lucide-react';
import type { RecommendedCrop, TabState } from '../../types/farmflate';
import { BottomNavigation } from '../common/BottomNavigation';
import { getDynamicWeather } from '../../services/farmEngine';

interface MainDashboardViewProps {
  userName?: string;
  analyzedRegion?: string;
  analyzedCropResult?: RecommendedCrop | null;
  onGoToExplore: () => void;
  onOpenAIChat: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
  isNewUser?: boolean;
}

export const MainDashboardView: React.FC<MainDashboardViewProps> = ({
  userName = '진우님',
  analyzedRegion = '전북 고창군',
  analyzedCropResult: _analyzedCropResult,
  onGoToExplore,
  onOpenAIChat,
  activeTab,
  onTabChange,
  isNewUser = false
}) => {
  const weather = getDynamicWeather(analyzedRegion);

  const [tasks, setTasks] = useState([
    { id: 1, title: '상추밭 물 주기', time: '오전 중', crop: 'lettuce', completed: false },
    { id: 2, title: '감자밭 서리 대비 덮개 씌우기', time: '오후 전', crop: 'potato', completed: false }
  ]);

  const toggleTask = (id: number) => {
    setTasks(prev => prev.map(t => t.id === id ? { ...t, completed: !t.completed } : t));
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar" style={{ backgroundColor: '#FFFFFF', padding: '24px 20px 96px 20px', overflowY: 'auto' }}>
        
        {/* Top Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <img
            src="/svg-assets/brand/wordmark.svg"
            alt="Farmflate"
            className="logo-wordmark"
            style={{ height: 28 }}
            onClick={() => onTabChange('home')}
          />
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <button style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <img src="/svg-assets/ui-icons/bell.svg" alt="알림" style={{ width: 22, height: 22 }} />
            </button>
            <div style={{
              width: 40, height: 40, borderRadius: '50%',
              backgroundColor: '#E9F7EC', border: '1.5px solid #2FA86A',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              overflow: 'hidden'
            }}>
              <img
                src="/svg-assets/brand/mascot/hello.svg"
                alt="사용자 프로필"
                style={{ width: 34, height: 34, objectFit: 'contain' }}
              />
            </div>
          </div>
        </div>

        {/* Dynamic User Greeting */}
        <div style={{ marginBottom: 18 }}>
          <h1 style={{ fontSize: '1.35rem', fontWeight: 850, color: '#202a24', lineHeight: 1.2, margin: '0 0 4px 0' }}>
            안녕하세요, {userName.endsWith('님') ? userName : `${userName}님`}
          </h1>
          <p style={{ fontSize: '0.8rem', color: '#6f7772', margin: 0, fontWeight: 500 }}>
            {isNewUser ? '오늘의 밭 상황을 확인해보세요.' : '오늘도 즐거운 농사 되세요!'}
          </p>
        </div>

        {/* Weather Card */}
        {isNewUser ? (
          <div style={{ width: '100%', height: 60, borderRadius: 14, backgroundColor: '#C8E8FA', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 24, color: '#6B90A6', fontSize: '0.9rem', fontWeight: 700 }}>
            날씨 데이터가 없어요
          </div>
        ) : (
          <div style={{
            position: 'relative', width: '100%', minHeight: 116, borderRadius: 14,
            background: 'linear-gradient(135deg, #bde8ff, #b7e6ff)',
            padding: '16px 18px', marginBottom: 24, boxSizing: 'border-box',
            boxShadow: 'inset 0 0 25px rgba(255, 255, 255, 0.22)',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center'
          }}>
            <div>
              <div style={{ fontSize: '0.72rem', color: '#668294', fontWeight: 600, marginBottom: 2 }}>{analyzedRegion}</div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, margin: '2px 0 4px 0' }}>
                <span style={{ fontSize: '2.2rem', fontWeight: 600, color: '#202a24', letterSpacing: '-0.06em' }}>
                  {weather.temp}<small style={{ fontSize: '1.1rem' }}>℃</small>
                </span>
                <span style={{ fontSize: '0.76rem', fontWeight: 700, color: '#52636b' }}>{weather.weatherState}</span>
              </div>
              <div style={{ fontSize: '0.74rem', color: '#587281', fontWeight: 500 }}>
                체감 {weather.feelsLike}℃ &nbsp;|&nbsp; 습도 {weather.humidity}% &nbsp;|&nbsp; 바람 {weather.wind}m/s
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <img src="/svg-assets/weather/partly-cloudy.svg" alt="날씨" style={{ width: 68, height: 58, objectFit: 'contain' }} />
              <div style={{ width: 1, height: 50, backgroundColor: 'rgba(88, 151, 181, 0.25)' }} />
              <div style={{ fontSize: '0.76rem', lineHeight: 1.6, color: '#3d5964', textAlign: 'right' }}>
                {weather.forecast}<br />
                <strong style={{ fontWeight: 800 }}>강수확률 {weather.rainProb}%</strong>
              </div>
            </div>
          </div>
        )}

        {/* Tasks Section */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <h2 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202a24', margin: 0 }}>
              오늘 해야 할 일
            </h2>
            {!isNewUser && (
              <span style={{ fontSize: '0.74rem', color: '#2FA86A', fontWeight: 700 }}>
                {tasks.filter(t => t.completed).length} / {tasks.length} 완료
              </span>
            )}
          </div>

          {isNewUser ? (
             <div style={{ width: '100%', height: 48, borderRadius: 12, backgroundColor: '#f2f4f3', display: 'flex', alignItems: 'center', padding: '0 16px', color: '#8e9691', fontSize: '0.85rem', fontWeight: 600 }}>
               오늘 해야 할 일이 없어요
             </div>
          ) : (
            <div style={{ border: '1px solid #dfe4e1', borderRadius: 14, overflow: 'hidden', backgroundColor: '#FFFFFF', boxSizing: 'border-box' }}>
              {tasks.map((task, idx) => (
                <div key={task.id} onClick={() => toggleTask(task.id)} style={{
                  display: 'grid', gridTemplateColumns: '38px minmax(0, 1fr) 54px 28px', alignItems: 'center',
                  padding: '14px 14px', borderBottom: idx < tasks.length - 1 ? '1px solid #e6e9e7' : 'none', cursor: 'pointer', gap: '4px'
                }}>
                  <img src={task.crop === 'lettuce' ? '/svg-assets/crops/lettuce.svg' : '/svg-assets/crops/potato.svg'} alt={task.title} style={{ width: 28, height: 28, objectFit: 'contain' }} />
                  <span style={{
                    fontSize: '0.9rem', fontWeight: 650, color: task.completed ? '#9CA3AF' : '#202a24',
                    textDecoration: task.completed ? 'line-through' : 'none', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'
                  }}>
                    {task.title}
                  </span>
                  <span style={{ fontSize: '0.74rem', color: '#969c98' }}>{task.time}</span>
                  <div style={{
                    width: 21, height: 21, borderRadius: '50%', border: task.completed ? 'none' : '1.6px solid #9ea7a1',
                    backgroundColor: task.completed ? '#2e9f5b' : 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#FFF', fontSize: '0.7rem'
                  }}>
                    {task.completed && '✓'}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Central CTA or Warning Card */}
        {isNewUser ? (
          <motion.div whileTap={{ scale: 0.98 }} onClick={onGoToExplore} style={{ position: 'relative', width: '100%', minHeight: 130, borderRadius: 20, background: 'linear-gradient(135deg, #d3efff, #f2f9ff)', padding: '24px 20px', marginBottom: 24, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'space-between', boxShadow: 'inset 0 0 20px rgba(255,255,255,0.4)' }}>
            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 900, color: '#191F28', margin: '0 0 6px 0', lineHeight: 1.3 }}>
                지역 입력하고<br />맞춤형 정보 받아보기
              </h2>
              <p style={{ fontSize: '0.8rem', color: '#668294', margin: 0, fontWeight: 500 }}>
                재배 희망지역, 관심작물을 입력 후 필수 확인하기
              </p>
            </div>
            <div style={{ width: 40, height: 40, borderRadius: '50%', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: '0 4px 12px rgba(0,0,0,0.05)', flexShrink: 0 }}>
              <ChevronRight size={24} color="#191F28" />
            </div>
          </motion.div>
        ) : (
          <div style={{ position: 'relative', width: '100%', minHeight: 160, border: '1px solid #ffe0a8', borderRadius: 14, background: 'linear-gradient(135deg, #fff8e8, #fff2d3)', padding: '18px 20px', marginBottom: 24, boxSizing: 'border-box', overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 7, color: '#ff7f2b', fontSize: '0.9rem', fontWeight: 850, marginBottom: 10 }}>
              ⚠️ 오늘 주의해야 할 밭이 1개 있어요
            </div>
            <div style={{ fontSize: '0.86rem', lineHeight: 1.5, color: '#626a65', marginBottom: 14 }}>
              <strong style={{ display: 'block', color: '#29312c', fontSize: '0.94rem', marginBottom: 2, fontWeight: 800 }}>
                상추밭 ({analyzedRegion})
              </strong>
              {weather.warningText}
            </div>
            <button onClick={() => onTabChange('myfield')} style={{ height: 32, padding: '0 14px', border: '1px solid #ffcfb1', borderRadius: 18, backgroundColor: '#FFFFFF', color: '#ff7d31', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer' }}>
              대응법 보기 ›
            </button>
            <img src="/svg-assets/weather/water-drop-alert.svg" alt="물방울 캐릭터" style={{ position: 'absolute', right: 12, bottom: 4, width: 84, height: 120, objectFit: 'contain' }} />
          </div>
        )}

        {/* Recommended Farming Advice */}
        <div>
          <h2 style={{ fontSize: '1.05rem', fontWeight: 850, color: '#202a24', marginBottom: 12 }}>
            {isNewUser ? '내 땅에 맞는 추천 농사 정보' : '상추에 맞는 추천 농사 정보'}
          </h2>

          {isNewUser ? (
             <div style={{ width: '100%', height: 60, borderRadius: 14, backgroundColor: '#e2f4e8', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#468661', fontSize: '0.88rem', fontWeight: 700 }}>
               아직 추천 정보가 없어요
             </div>
          ) : (
            <motion.div whileTap={{ scale: 0.98 }} onClick={onGoToExplore} style={{ width: '100%', minHeight: 73, border: '1px solid #dbe6de', borderRadius: 14, background: 'linear-gradient(135deg, #fdfffd, #f7fbf8)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 16px', boxSizing: 'border-box', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <img src="/svg-assets/crops/lettuce.svg" alt="상추" style={{ width: 44, height: 44, objectFit: 'contain' }} />
                <div>
                  <strong style={{ fontSize: '0.88rem', color: '#306847', fontWeight: 800 }}>장마철 상추 무름병 예방법</strong>
                  <p style={{ margin: '3px 0 0 0', fontSize: '0.76rem', color: '#747b76' }}>습도 관리와 배수 개선이 중요해요.</p>
                </div>
              </div>
              <ChevronRight size={20} color="#24513a" />
            </motion.div>
          )}
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
