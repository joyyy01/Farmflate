import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronRight, AlertTriangle, MoveRight } from 'lucide-react';
import type { RecommendedCrop, TabState } from '../../types/farmflate';
import { BottomNavigation } from '../common/BottomNavigation';
import { getDynamicWeather } from '../../services/farmEngine';
import type { HomeData } from '../../services/api';

interface MainDashboardViewProps {
  userName?: string;
  analyzedRegion?: string;
  analyzedCropResult?: RecommendedCrop | null;
  homeData?: HomeData | null;
  onGoToExplore: () => void;
  onOpenReport?: () => void;
  onOpenAIChat: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
  isNewUser?: boolean;
}

export const MainDashboardView: React.FC<MainDashboardViewProps> = ({
  userName = '사용자님',
  analyzedRegion = '전북 고창군',
  analyzedCropResult: _analyzedCropResult,
  homeData,
  onGoToExplore,
  onOpenReport,
  onOpenAIChat,
  activeTab,
  onTabChange,
  isNewUser = false
}) => {
  // Direct REAL API Data Extraction from Backend /home Endpoint
  const regionName = homeData?.latestRegionAnalysis?.regionName || analyzedRegion;
  const shortRegion = regionName.split(' ').pop() || regionName;
  const dynamicWeather = getDynamicWeather(regionName);

  // Weather parameters from Real Backend API
  const temp = homeData?.weather?.temperature ?? dynamicWeather.temp;
  const minTemp = homeData?.weather?.minTemperature ?? (temp - 3);
  const maxTemp = homeData?.weather?.maxTemperature ?? (temp + 4);
  const rainProb = homeData?.weather?.precipitationProbability ?? dynamicWeather.rainProb;
  const humidity = dynamicWeather.humidity;
  const wind = dynamicWeather.wind;
  
  // Real-time matched weather state & forecast phrasing
  const weatherStateText = rainProb > 50 ? '비 소식' : dynamicWeather.weatherState;
  const forecastText = rainProb > 50 ? '집중호우에 유의하세요' : dynamicWeather.forecast;

  // Action / Risk parameters from Backend API
  const actionTitle = homeData?.todayAction?.title || '밭 주변 배수로가 막히지 않았는지 점검하세요.';
  const actionReason = homeData?.todayAction?.reason || '향후 강수가 특정 시기에 몰릴 수 있어 밭 주변 배수 관리가 중요해요.';

  // Latest Region Analysis & Recommended Crop from Backend API
  const topCropName = homeData?.latestRegionAnalysis?.topCrop?.cropName || '감자';
  const topCropScore = homeData?.latestRegionAnalysis?.topCrop?.score || 96;
  const topCropReason = homeData?.latestRegionAnalysis?.topCrop?.reason || '서늘한 기후 조건과 배수가 우수한 토양 생육 환경에 잘 맞습니다.';

  const getCropIcon = (cropName: string) => {
    if (cropName.includes('상추')) return '/svg-assets/crops/lettuce.svg';
    if (cropName.includes('사과')) return '/svg-assets/crops/apple.svg';
    if (cropName.includes('배')) return '/svg-assets/crops/pear.svg';
    if (cropName.includes('고추')) return '/svg-assets/crops/pepper.svg';
    return '/svg-assets/crops/potato.svg';
  };

  const topCropIcon = getCropIcon(topCropName);

  const [tasks, setTasks] = useState([
    { id: 1, title: `${shortRegion} 토양 수분 및 pH 점검`, time: '오전 중', completed: false, icon: '/svg-assets/crops/soil-sprout.svg' },
    { id: 2, title: actionTitle, time: '오후 전', completed: false, icon: '/svg-assets/weather/rain.svg' }
  ]);

  const toggleTask = (id: number) => {
    setTasks(prev => prev.map(t => t.id === id ? { ...t, completed: !t.completed } : t));
  };

  const handleReportViewClick = () => {
    if (onOpenReport) {
      onOpenReport();
    } else {
      onGoToExplore();
    }
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar" style={{ backgroundColor: '#FFFFFF', padding: '32px 20px 96px 20px', overflowY: 'auto' }}>
        
        {/* Top Wordmark Logo */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <img
            src="/svg-assets/brand/wordmark.svg"
            alt="Farmflate"
            className="logo-wordmark"
            style={{ height: 28, cursor: 'pointer' }}
            onClick={() => onTabChange('home')}
          />
        </div>

        {/* Dynamic User Greeting */}
        <div style={{ marginBottom: 22 }}>
          <h1 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#191F28', lineHeight: 1.2, margin: '0 0 5px 0', letterSpacing: '-0.03em' }}>
            안녕하세요, {userName.endsWith('님') ? userName : `${userName}님`}
          </h1>
          <p style={{ fontSize: '0.84rem', color: '#8B95A1', margin: 0, fontWeight: 500, letterSpacing: '-0.01em' }}>
            {isNewUser ? '오늘의 밭 상황을 확인해보세요' : '오늘도 즐거운 농사 되세요!'}
          </p>
        </div>

        {/* Weather Card - Clean Natural Layout (No Box Backdrop) */}
        {isNewUser ? (
          <div
            style={{
              backgroundColor: '#C5EAFA', borderRadius: 20,
              border: '1px solid rgba(255, 255, 255, 0.4)',
              height: 62, width: '100%', marginBottom: 20, cursor: 'default',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}
          >
            <span style={{ fontSize: '0.94rem', fontWeight: 750, color: '#FFFFFF', letterSpacing: '-0.02em' }}>
              날씨 데이터가 없어요
            </span>
          </div>
        ) : (
          <div style={{
            position: 'relative', width: '100%', borderRadius: 22,
            background: 'linear-gradient(135deg, #F0F9FF 0%, #E0F2FE 100%)',
            border: '1px solid #BAE6FD',
            padding: '20px 20px 18px 20px', marginBottom: 20, boxSizing: 'border-box',
            display: 'flex', flexDirection: 'column', gap: 12
          }}>
            {/* Top Row: Region & Temperature + Weather Icon & Forecast Text */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div style={{ fontSize: '0.76rem', color: '#0369A1', fontWeight: 750, letterSpacing: '-0.01em', marginBottom: 2 }}>
                  {regionName}
                </div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                  <span style={{ fontSize: '2.1rem', fontWeight: 900, color: '#0C4A6E', letterSpacing: '-0.05em', lineHeight: 1 }}>
                    {temp}<small style={{ fontSize: '1.1rem' }}>℃</small>
                  </span>
                  <span style={{ fontSize: '0.8rem', fontWeight: 800, color: '#0284C7' }}>
                    {weatherStateText}
                  </span>
                </div>
              </div>

              {/* Right Side: Natural Icon + Forecast Text (No Artificial Box Backdrop) */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <img
                  src={rainProb > 50 ? '/svg-assets/weather/rain.svg' : '/svg-assets/weather/partly-cloudy.svg'}
                  alt="날씨"
                  style={{ width: 48, height: 48, objectFit: 'contain' }}
                />

                <div style={{ width: 1, height: 42, backgroundColor: '#BAE6FD' }} />

                <div style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                  <span style={{ fontSize: '0.78rem', fontWeight: 800, color: '#0C4A6E', display: 'block', marginBottom: 2 }}>
                    {forecastText}
                  </span>
                  <span style={{ fontSize: '0.82rem', fontWeight: 900, color: '#0284C7' }}>
                    강수확률 {rainProb}%
                  </span>
                </div>
              </div>
            </div>

            {/* Bottom Row: Weather Metrics strictly on 1 Line (No Line Break!) */}
            <div style={{
              fontSize: '0.74rem', color: '#0369A1', fontWeight: 650,
              whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
              paddingTop: 10, borderTop: '1px solid rgba(186, 230, 253, 0.6)',
              display: 'flex', justifyContent: 'space-between', alignItems: 'center'
            }}>
              <span>최저 <strong>{minTemp}℃</strong></span>
              <span style={{ color: '#BAE6FD' }}>|</span>
              <span>최고 <strong>{maxTemp}℃</strong></span>
              <span style={{ color: '#BAE6FD' }}>|</span>
              <span>습도 <strong>{humidity}%</strong></span>
              <span style={{ color: '#BAE6FD' }}>|</span>
              <span>바람 <strong>{wind}m/s</strong></span>
            </div>
          </div>
        )}

        {/* Today's Tasks Section */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <h2 style={{ fontSize: '1.12rem', fontWeight: 900, color: '#191F28', margin: 0, letterSpacing: '-0.02em' }}>
              오늘 해야 할 일
            </h2>
            {!isNewUser && (
              <span style={{ fontSize: '0.76rem', color: '#2FA86A', fontWeight: 800 }}>
                {tasks.filter(t => t.completed).length} / {tasks.length} 완료
              </span>
            )}
          </div>

          {isNewUser ? (
             <div style={{
               backgroundColor: '#F8FAF8', borderRadius: 20, border: '1px solid #EAEFEA',
               height: 56, padding: '0 20px', display: 'flex', alignItems: 'center'
             }}>
               <span style={{ fontSize: '0.88rem', color: '#6E7671', fontWeight: 650, letterSpacing: '-0.01em' }}>
                 오늘 해야할 일이 없어요
               </span>
             </div>
          ) : (
            <div style={{ border: '1px solid #E5E8EB', borderRadius: 20, overflow: 'hidden', backgroundColor: '#FFFFFF', boxSizing: 'border-box' }}>
              {tasks.map((task, idx) => (
                <div key={task.id} onClick={() => toggleTask(task.id)} style={{
                  display: 'grid', gridTemplateColumns: '32px minmax(0, 1fr) 48px 24px', alignItems: 'center',
                  padding: '16px 18px', borderBottom: idx < tasks.length - 1 ? '1px solid #F3F4F6' : 'none', cursor: 'pointer', gap: '8px'
                }}>
                  <img src={task.icon} alt={task.title} style={{ width: 24, height: 24, objectFit: 'contain' }} />
                  <span style={{
                    fontSize: '0.88rem', fontWeight: 700, color: task.completed ? '#9CA3AF' : '#191F28',
                    textDecoration: task.completed ? 'line-through' : 'none', lineHeight: 1.35
                  }}>
                    {task.title}
                  </span>
                  <span style={{ fontSize: '0.74rem', color: '#8E9892', fontWeight: 600, textAlign: 'right' }}>{task.time}</span>
                  <div style={{
                    width: 22, height: 22, borderRadius: '50%', border: task.completed ? 'none' : '1.8px solid #CBD5E1',
                    backgroundColor: task.completed ? '#2FA86A' : 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#FFF', fontSize: '0.75rem', fontWeight: 900, justifySelf: 'end'
                  }}>
                    {task.completed && '✓'}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Primary Action Banner Card (New User) OR Today's Alert Action Card (Existing User) */}
        {isNewUser ? (
          <motion.div
            whileTap={{ scale: 0.98 }}
            onClick={onGoToExplore}
            style={{
              position: 'relative', width: '100%', borderRadius: 26,
              background: 'linear-gradient(135deg, #FFFFFF 0%, #F1F9FE 45%, #D2F0FF 100%)',
              border: '1px solid #C4E9FC',
              padding: '28px 24px 24px 24px', marginBottom: 20, cursor: 'pointer',
              boxSizing: 'border-box'
            }}
          >
            <h2 style={{ fontSize: '1.38rem', fontWeight: 900, color: '#191F28', margin: '0 0 6px 0', lineHeight: 1.32, letterSpacing: '-0.03em' }}>
              지역 입력하고<br />맞춤형 정보 받아보기
            </h2>
            <p style={{ fontSize: '0.84rem', color: '#557285', margin: 0, fontWeight: 500, letterSpacing: '-0.01em' }}>
              재배 희망지역, 희망작물 입력 후 점수 확인하기
            </p>
            
            {/* Bottom Right Arrow in Circular White Button Backdrop */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 14 }}>
              <div style={{
                width: 42, height: 42, borderRadius: '50%',
                backgroundColor: '#FFFFFF', border: '1px solid #C4E9FC',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                boxShadow: '0 2px 8px rgba(0, 0, 0, 0.03)'
              }}>
                <MoveRight size={22} color="#191F28" strokeWidth={2.4} />
              </div>
            </div>
          </motion.div>
        ) : (
          <div style={{ position: 'relative', width: '100%', minHeight: 154, border: '1px solid #FFE0A8', borderRadius: 20, backgroundColor: '#FFF8E8', padding: '20px', marginBottom: 20, boxSizing: 'border-box', overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#FF7F2B', fontSize: '0.86rem', fontWeight: 850, marginBottom: 8 }}>
              <AlertTriangle size={18} color="#FF7F2B" /> 오늘 조치사항 ({shortRegion})
            </div>
            <div style={{ fontSize: '0.85rem', lineHeight: 1.5, color: '#626A65', marginBottom: 14, paddingRight: 80 }}>
              <strong style={{ display: 'block', color: '#191F28', fontSize: '0.94rem', marginBottom: 3, fontWeight: 850 }}>
                {actionTitle}
              </strong>
              {actionReason}
            </div>
            <button onClick={handleReportViewClick} style={{ height: 34, padding: '0 16px', border: '1px solid #FFCFB1', borderRadius: 18, backgroundColor: '#FFFFFF', color: '#FF7D31', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer' }}>
              지역 리포트 보기 ›
            </button>
            <img src="/svg-assets/weather/water-drop-alert.svg" alt="물방울 캐릭터" style={{ position: 'absolute', right: 10, bottom: 4, width: 80, height: 114, objectFit: 'contain' }} />
          </div>
        )}

        {/* Recommended Farming Advice Section */}
        <div style={{ marginBottom: 16 }}>
          <h2 style={{ fontSize: '1.12rem', fontWeight: 900, color: '#191F28', marginBottom: 12, letterSpacing: '-0.02em' }}>
            {isNewUser ? '내 밭에 맞는 추천 농사 정보' : `${shortRegion} 추천 작물 정보`}
          </h2>

          {isNewUser ? (
             <div
               style={{
                 backgroundColor: '#E4F3E7', borderRadius: 20,
                 border: '1px solid #D1EADB',
                 height: 60, width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center',
                 cursor: 'default'
               }}
             >
               <span style={{ fontSize: '0.9rem', fontWeight: 800, color: '#154F36', letterSpacing: '-0.02em' }}>
                 아직 추천 정보가 없어요
               </span>
             </div>
          ) : (
            <motion.div whileTap={{ scale: 0.98 }} onClick={handleReportViewClick} style={{ width: '100%', border: '1px solid #E5E8EB', borderRadius: 20, backgroundColor: '#F8FAF8', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 18px', boxSizing: 'border-box', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <img src={topCropIcon} alt={topCropName} style={{ width: 44, height: 44, objectFit: 'contain' }} />
                <div>
                  <strong style={{ fontSize: '0.9rem', color: '#154F36', fontWeight: 850 }}>TOP 1 추천: {topCropName} ({topCropScore}점)</strong>
                  <p style={{ margin: '3px 0 0 0', fontSize: '0.78rem', color: '#6E7671', fontWeight: 500, lineHeight: 1.4 }}>{topCropReason}</p>
                </div>
              </div>
              <ChevronRight size={20} color="#154F36" />
            </motion.div>
          )}
        </div>

      </div>

      {/* Floating AI Button - 100% UNIFIED VECTOR SVG ICON */}
      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <img src="/svg-assets/ui-icons/ai-chat.svg" alt="AI 채팅" style={{ width: 26, height: 26, filter: 'brightness(0) invert(1)' }} />
      </button>

      {/* Bottom Navigation */}
      <BottomNavigation
        activeTab={activeTab}
        onTabChange={onTabChange}
        onOpenAIChat={onOpenAIChat}
      />
    </div>
  );
};
