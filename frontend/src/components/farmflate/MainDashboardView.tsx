import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronRight, AlertTriangle, Compass, CalendarCheck, Sparkles } from 'lucide-react';
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
  const regionName = homeData?.latestRegionAnalysis?.regionName || analyzedRegion;
  const shortRegion = regionName.split(' ').pop() || regionName;
  const dynamicWeather = getDynamicWeather(regionName);

  // Weather parameters from Backend or Regional Dynamic Engine
  const temp = homeData?.weather?.temperature ?? dynamicWeather.temp;
  const minTemp = homeData?.weather?.minTemperature ?? (temp - 3);
  const maxTemp = homeData?.weather?.maxTemperature ?? (temp + 4);
  const rainProb = homeData?.weather?.precipitationProbability ?? dynamicWeather.rainProb;
  const humidity = dynamicWeather.humidity;
  const wind = dynamicWeather.wind;
  
  // Real-time matched weather state & forecast phrasing
  const weatherStateText = rainProb > 50 ? '비 소식' : dynamicWeather.weatherState;
  const forecastText = rainProb > 50 ? '집중호우에 유의하세요' : dynamicWeather.forecast;

  // Action / Risk parameters from Backend
  const actionTitle = homeData?.todayAction?.title || '밭 주변 배수로 점검';
  const actionReason = homeData?.todayAction?.reason || '집중호우에 대비해 물 빠짐을 점검해 주세요.';

  // Latest Region Analysis & Recommended Crop from Backend
  const topCropName = homeData?.latestRegionAnalysis?.topCrop?.cropName || '감자';
  const topCropScore = homeData?.latestRegionAnalysis?.topCrop?.score || 91;
  const topCropReason = homeData?.latestRegionAnalysis?.topCrop?.reason || '서늘한 기후와 배수가 우수한 토양 환경에 적합합니다.';

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
        
        {/* Top Header */}
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
        <div style={{ marginBottom: 20 }}>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 900, color: '#191F28', lineHeight: 1.2, margin: '0 0 4px 0' }}>
            안녕하세요, {userName.endsWith('님') ? userName : `${userName}님`}
          </h1>
          <p style={{ fontSize: '0.84rem', color: '#6F7772', margin: 0, fontWeight: 500 }}>
            {isNewUser ? '지역을 분석하고 맞춤형 농사 정보를 받아보세요.' : '오늘도 즐거운 농사 되세요!'}
          </p>
        </div>

        {/* Dynamic Weather Card */}
        {isNewUser ? (
          <div
            onClick={onGoToExplore}
            style={{
              backgroundColor: '#F8FAF8', borderRadius: 20, border: '1px solid #EAEFEA',
              padding: '18px 20px', marginBottom: 24, cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              transition: 'all 0.2s ease'
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ width: 44, height: 44, borderRadius: 14, backgroundColor: '#E0F2FE', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <img src="/svg-assets/weather/partly-cloudy.svg" alt="날씨" style={{ width: 28, height: 28, objectFit: 'contain' }} />
              </div>
              <div>
                <div style={{ fontSize: '0.9rem', fontWeight: 850, color: '#191F28', marginBottom: 2 }}>실시간 기상 정보</div>
                <div style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 500 }}>지역을 탐색하고 맞춤 날씨 예보를 확인해보세요.</div>
              </div>
            </div>
            <ChevronRight size={18} color="#9CA3AF" />
          </div>
        ) : (
          <div style={{
            position: 'relative', width: '100%', minHeight: 116, borderRadius: 20,
            backgroundColor: '#E0F2FE', border: '1px solid #BAE6FD',
            padding: '18px 20px', marginBottom: 24, boxSizing: 'border-box',
            display: 'flex', justifyContent: 'space-between', alignItems: 'center'
          }}>
            <div>
              <div style={{ fontSize: '0.74rem', color: '#0369A1', fontWeight: 700, marginBottom: 2 }}>{regionName}</div>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, margin: '2px 0 4px 0' }}>
                <span style={{ fontSize: '2.2rem', fontWeight: 800, color: '#0C4A6E', letterSpacing: '-0.06em' }}>
                  {temp}<small style={{ fontSize: '1.1rem' }}>℃</small>
                </span>
                <span style={{ fontSize: '0.76rem', fontWeight: 750, color: '#0284C7' }}>
                  {weatherStateText}
                </span>
              </div>
              <div style={{ fontSize: '0.74rem', color: '#0369A1', fontWeight: 600 }}>
                최저 {minTemp}℃ &nbsp;|&nbsp; 최고 {maxTemp}℃ &nbsp;|&nbsp; 습도 {humidity}% &nbsp;|&nbsp; 바람 {wind}m/s
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <img
                src={rainProb > 50 ? '/svg-assets/weather/rain.svg' : '/svg-assets/weather/partly-cloudy.svg'}
                alt="날씨"
                style={{ width: 68, height: 58, objectFit: 'contain' }}
              />
              <div style={{ width: 1, height: 50, backgroundColor: '#BAE6FD' }} />
              <div style={{ fontSize: '0.76rem', lineHeight: 1.6, color: '#0C4A6E', textAlign: 'right' }}>
                {forecastText}<br />
                <strong style={{ fontWeight: 800 }}>강수확률 {rainProb}%</strong>
              </div>
            </div>
          </div>
        )}

        {/* Today's Tasks Section */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <h2 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#191F28', margin: 0 }}>
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
               padding: '18px 20px', display: 'flex', alignItems: 'center', gap: 14
             }}>
               <div style={{ width: 42, height: 42, borderRadius: 14, backgroundColor: '#EDF7ED', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                 <CalendarCheck size={20} color="#2FA86A" />
               </div>
               <div>
                 <div style={{ fontSize: '0.88rem', fontWeight: 850, color: '#191F28', marginBottom: 2 }}>아직 할 일이 없습니다</div>
                 <div style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 500 }}>지역 분석 등록 시 일별 농가 조치 사항이 자동 생성됩니다.</div>
               </div>
             </div>
          ) : (
            <div style={{ border: '1px solid #E5E8EB', borderRadius: 20, overflow: 'hidden', backgroundColor: '#FFFFFF', boxSizing: 'border-box' }}>
              {tasks.map((task, idx) => (
                <div key={task.id} onClick={() => toggleTask(task.id)} style={{
                  display: 'grid', gridTemplateColumns: '38px minmax(0, 1fr) 54px 28px', alignItems: 'center',
                  padding: '16px 18px', borderBottom: idx < tasks.length - 1 ? '1px solid #F1F5F9' : 'none', cursor: 'pointer', gap: '4px'
                }}>
                  <img src={task.icon} alt={task.title} style={{ width: 26, height: 26, objectFit: 'contain' }} />
                  <span style={{
                    fontSize: '0.9rem', fontWeight: 700, color: task.completed ? '#9CA3AF' : '#191F28',
                    textDecoration: task.completed ? 'line-through' : 'none', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'
                  }}>
                    {task.title}
                  </span>
                  <span style={{ fontSize: '0.74rem', color: '#8E9892', fontWeight: 600 }}>{task.time}</span>
                  <div style={{
                    width: 22, height: 22, borderRadius: '50%', border: task.completed ? 'none' : '1.8px solid #CBD5E1',
                    backgroundColor: task.completed ? '#2FA86A' : 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#FFF', fontSize: '0.75rem', fontWeight: 900
                  }}>
                    {task.completed && '✓'}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Primary Action Banner Card */}
        {isNewUser ? (
          <motion.div
            whileTap={{ scale: 0.98 }}
            onClick={onGoToExplore}
            style={{
              position: 'relative', width: '100%', borderRadius: 22,
              backgroundColor: '#EDF7ED', border: '1px solid #D4EDDA',
              padding: '22px 20px', marginBottom: 24, cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              boxSizing: 'border-box'
            }}
          >
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#2FA86A', fontSize: '0.78rem', fontWeight: 850, marginBottom: 4 }}>
                <Compass size={16} /> 맞춤 토양 분석
              </div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 900, color: '#191F28', margin: '0 0 6px 0', lineHeight: 1.3 }}>
                지역 입력하고<br />맞춤형 정보 받아보기
              </h2>
              <p style={{ fontSize: '0.78rem', color: '#556E5D', margin: 0, fontWeight: 500 }}>
                재배 희망지역, 관심작물을 입력 후 필수 확인하기
              </p>
            </div>
            <div style={{ width: 44, height: 44, borderRadius: '50%', backgroundColor: '#FFFFFF', border: '1px solid #D4EDDA', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <ChevronRight size={24} color="#2FA86A" />
            </div>
          </motion.div>
        ) : (
          <div style={{ position: 'relative', width: '100%', minHeight: 150, border: '1px solid #FFE0A8', borderRadius: 20, backgroundColor: '#FFF8E8', padding: '20px', marginBottom: 24, boxSizing: 'border-box', overflow: 'hidden' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#FF7F2B', fontSize: '0.88rem', fontWeight: 850, marginBottom: 10 }}>
              <AlertTriangle size={18} color="#FF7F2B" /> 오늘 조치사항 ({regionName})
            </div>
            <div style={{ fontSize: '0.86rem', lineHeight: 1.5, color: '#626A65', marginBottom: 14 }}>
              <strong style={{ display: 'block', color: '#191F28', fontSize: '0.94rem', marginBottom: 2, fontWeight: 850 }}>
                {actionTitle}
              </strong>
              {actionReason}
            </div>
            <button onClick={handleReportViewClick} style={{ height: 34, padding: '0 16px', border: '1px solid #FFCFB1', borderRadius: 18, backgroundColor: '#FFFFFF', color: '#FF7D31', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer' }}>
              지역 리포트 보기 ›
            </button>
            <img src="/svg-assets/weather/water-drop-alert.svg" alt="물방울 캐릭터" style={{ position: 'absolute', right: 12, bottom: 4, width: 84, height: 120, objectFit: 'contain' }} />
          </div>
        )}

        {/* Recommended Farming Advice */}
        <div style={{ marginBottom: 16 }}>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#191F28', marginBottom: 12 }}>
            {isNewUser ? '내 땅에 맞는 추천 농사 정보' : `${regionName} 추천 작물 정보`}
          </h2>

          {isNewUser ? (
             <div
               onClick={onGoToExplore}
               style={{
                 backgroundColor: '#F8FAF8', borderRadius: 20, border: '1px solid #EAEFEA',
                 padding: '18px 20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                 cursor: 'pointer'
               }}
             >
               <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                 <div style={{ width: 42, height: 42, borderRadius: 14, backgroundColor: '#E9F7EC', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                   <Sparkles size={20} color="#2FA86A" />
                 </div>
                 <div>
                   <div style={{ fontSize: '0.88rem', fontWeight: 850, color: '#191F28', marginBottom: 2 }}>추천 정보 준비 중</div>
                   <div style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 500 }}>지역 탐색을 시작하시면 흙과 기후 맞춤 작물이 추천됩니다.</div>
                 </div>
               </div>
               <ChevronRight size={18} color="#9CA3AF" />
             </div>
          ) : (
            <motion.div whileTap={{ scale: 0.98 }} onClick={handleReportViewClick} style={{ width: '100%', border: '1px solid #E5E8EB', borderRadius: 20, backgroundColor: '#F8FAF8', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 18px', boxSizing: 'border-box', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                <img src={topCropIcon} alt={topCropName} style={{ width: 44, height: 44, objectFit: 'contain' }} />
                <div>
                  <strong style={{ fontSize: '0.9rem', color: '#154F36', fontWeight: 850 }}>TOP 1 추천: {topCropName} ({topCropScore}점)</strong>
                  <p style={{ margin: '3px 0 0 0', fontSize: '0.78rem', color: '#6E7671', fontWeight: 500 }}>{topCropReason}</p>
                </div>
              </div>
              <ChevronRight size={20} color="#154F36" />
            </motion.div>
          )}
        </div>

      </div>

      {/* Floating AI Button - Standardized 100% vector SVG icon design across all screens */}
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
