import React from 'react';
import { motion } from 'framer-motion';
import { AlertTriangle, MoveRight, Bot, BarChart3, ChevronRight } from 'lucide-react';
import type { TabState } from '../../types/farmflate';
import type { FieldProfile } from '../../types/report';
import { BottomNavigation } from '../common/BottomNavigation';
import type { HomeData } from '../../services/api';
import { WEATHER_ILLUSTRATIONS, snapshotFromBackend } from '../../services/weatherService';
import { RecommendedCropCarousel } from './RecommendedCropCarousel';

interface MainDashboardViewProps {
  userName?: string;
  analyzedRegion?: string;
  homeData?: HomeData | null;
  fields?: FieldProfile[];
  loadError?: string | null;
  onGoToExplore: () => void;
  onOpenReport?: () => void;
  onOpenAIChat: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
  isNewUser?: boolean;
}

export const MainDashboardView: React.FC<MainDashboardViewProps> = ({
  userName = '사용자님',
  analyzedRegion,
  homeData,
  fields = [],
  loadError,
  onGoToExplore,
  onOpenReport,
  onOpenAIChat,
  activeTab,
  onTabChange,
  isNewUser = false
}) => {
  // Direct REAL API Data Extraction from Backend /home Endpoint
  const regionName = homeData?.latestRegionAnalysis?.regionName || analyzedRegion || '지역 분석 전';
  const shortRegion = regionName.split(' ').pop() || regionName;

  // Weather: derived entirely from the backend /api/home weather DTO (KMA data).
  const weather = snapshotFromBackend(homeData?.weather);
  const temp = weather?.temperature ?? 0;
  const minTemp = weather?.minTemperature ?? temp - 3;
  const maxTemp = weather?.maxTemperature ?? temp + 4;
  const rainProb = weather?.precipitationProbability ?? 0;
  const condition = weather?.condition ?? 'clear';
  const weatherStateText = weather?.conditionLabel ?? '';
  const forecastText = weather?.forecastText ?? '';
  const humidity = weather?.humidity ?? null;
  const wind = weather?.windSpeed ?? null;

  // The home summary is grounded in each registered field's backend-generated daily status.
  const attentionFields = fields.filter(field => field.dailyStatus === 'CAUTION' || field.dailyStatus === 'DANGER');
  const attentionFieldCount = attentionFields.length;
  const visibleAttentionFields = attentionFields.slice(0, 2);

  // Latest Region Analysis: TOP 1-3 recommended crops
  const recommendedCrops = homeData?.latestRegionAnalysis?.recommendedCrops ?? [];

  const handleReportViewClick = () => {
    if (onOpenReport) {
      onOpenReport();
      return;
    }
    onGoToExplore();
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar home-dashboard__content">

        {/* Top Wordmark Logo */}
        <div className="home-dashboard__brand-row">
          <img
            src="/assets/brand-wordmark-new.png"
            alt="Farmflate"
            className="home-dashboard__brand"
            onClick={() => onTabChange('home')}
          />
        </div>

        {/* Dynamic User Greeting */}
        <div className="home-dashboard__greeting">
          <h1>
            안녕하세요, {userName.endsWith('님') ? userName : `${userName}님`}
          </h1>
          <p>
            {isNewUser ? '오늘의 밭 상황을 확인해보세요' : '오늘도 즐거운 농사 되세요!'}
          </p>
        </div>

        {/* Weather Card - Clean Natural Layout (No Box Backdrop) */}
        {isNewUser ? (
          <div
            style={{
              position: 'relative',
              borderRadius: 20,
              border: '1px solid rgba(255, 255, 255, 0.4)',
              height: 238, width: '100%', marginTop: 10, marginBottom: 20, cursor: 'default',
              display: 'flex', alignItems: 'center',
              overflow: 'hidden'
            }}
          >
            <img
              src="/assets/home-weather-empty.png"
              alt=""
              style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
            />
            <div style={{ position: 'relative', padding: '0 0 0 22px', maxWidth: '62%' }}>
              <div style={{ fontSize: '1.38rem', fontWeight: 700, color: '#0369A1', letterSpacing: '-0.02em', marginTop: -80, marginBottom: 5 }}>
                {loadError || '날씨 데이터가 없어요'}
              </div>
              <div style={{ fontSize: '0.78rem', fontWeight: 600, color: '#3A5A6B', letterSpacing: '-0.01em', lineHeight: 1.45 }}>
                기상 정보를 입력하면 맞춤 관리가 가능해요
              </div>
            </div>
          </div>
        ) : (
          <div style={{
            position: 'relative', width: '100%', minHeight: 192, borderRadius: 22,
            border: '1px solid #BAE6FD', overflow: 'hidden',
            marginBottom: 20, boxSizing: 'border-box'
          }}>
            <img
              src={WEATHER_ILLUSTRATIONS[condition]}
              alt=""
              style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
            />
            <div style={{
              position: 'absolute', inset: 0,
              background: 'linear-gradient(90deg, rgba(255,255,255,0.92) 0%, rgba(255,255,255,0.72) 40%, rgba(255,255,255,0.15) 75%)'
            }} />

            <div style={{ position: 'relative', padding: '20px 20px 16px 20px', display: 'flex', flexDirection: 'column', gap: 10, height: '100%', boxSizing: 'border-box' }}>
              <div style={{ fontSize: '0.76rem', color: '#0369A1', fontWeight: 750, letterSpacing: '-0.01em' }}>
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

              <div>
                <span style={{ fontSize: '0.78rem', fontWeight: 800, color: '#0C4A6E', display: 'block', marginBottom: 2 }}>
                  {forecastText}
                </span>
                <span style={{ fontSize: '0.82rem', fontWeight: 900, color: '#0284C7' }}>
                  강수확률 {rainProb}%
                </span>
              </div>

              {/* Bottom Row: Weather Metrics on 1 Line, own backdrop for legibility over the illustration */}
              <div style={{
                marginTop: 'auto',
                fontSize: '0.74rem', color: '#0369A1', fontWeight: 650,
                whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                backgroundColor: 'rgba(255,255,255,0.82)', borderRadius: 12,
                padding: '8px 12px',
                display: 'flex', justifyContent: 'space-between', alignItems: 'center'
              }}>
                <span>최저 <strong>{minTemp}℃</strong></span>
                <span style={{ color: '#BAE6FD' }}>|</span>
                <span>최고 <strong>{maxTemp}℃</strong></span>
                <span style={{ color: '#BAE6FD' }}>|</span>
                <span>습도 <strong>{humidity != null ? `${humidity}%` : '데이터 없음'}</strong></span>
                <span style={{ color: '#BAE6FD' }}>|</span>
                <span>바람 <strong>{wind != null ? `${wind}m/s` : '데이터 없음'}</strong></span>
              </div>
            </div>
          </div>
        )}

        {/* Primary Action Banner Card (New User) OR Today's Alert Action Card (Existing User) */}
        {isNewUser ? (
          <motion.div
            whileTap={{ scale: 0.98 }}
            onClick={onGoToExplore}
            style={{
              position: 'relative', width: '100%', height: 200, borderRadius: 26,
              border: '1px solid #C4E9FC',
              padding: '28px 24px 24px 24px', marginBottom: 20, cursor: 'pointer',
              boxSizing: 'border-box', overflow: 'hidden'
            }}
          >
            <img
              src="/assets/home-region-input.png"
              alt=""
              style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
            />

            <h2 style={{ position: 'relative', fontSize: '1.38rem', fontWeight: 700, color: '#191F28', margin: '0 0 6px 0', lineHeight: 1.32, letterSpacing: '-0.03em' }}>
              지역 입력하고<br />맞춤형 정보 받아보기
            </h2>
            <p style={{ position: 'relative', fontSize: '0.84rem', color: '#557285', margin: 0, fontWeight: 500, letterSpacing: '-0.01em' }}>
              재배 희망지역, 희망작물 입력 후 점수 확인하기
            </p>

            {/* Bottom Right Arrow in Circular White Button Backdrop */}
            <div style={{ position: 'relative', display: 'flex', justifyContent: 'flex-end', marginTop: 14 }}>
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
          <>
            {/* 지역 리포트 바로가기: reopen the region report the user already received */}
            <button
              onClick={handleReportViewClick}
              style={{
                width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                gap: 12, padding: '16px 18px', marginBottom: 20, boxSizing: 'border-box',
                backgroundColor: '#FFFFFF', border: '1px solid #E5E8EB', borderRadius: 18,
                cursor: 'pointer'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{
                  width: 34, height: 34, borderRadius: '50%', backgroundColor: '#E9F9EF',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
                }}>
                  <BarChart3 size={18} color="#2FA86A" />
                </div>
                <span style={{ fontSize: '0.92rem', fontWeight: 800, color: '#191F28' }}>
                  지역 리포트 바로가기
                </span>
              </div>
              <ChevronRight size={20} color="#CBD5E1" />
            </button>

            {/* 주의 구역 현황 */}
            <div style={{ marginBottom: 16 }}>
              <h2 style={{ fontSize: '1.05rem', fontWeight: 900, color: '#191F28', marginBottom: 12, letterSpacing: '-0.02em' }}>
                주의 구역 현황
              </h2>
              <div style={{ position: 'relative', width: '100%', minHeight: 154, border: '1px solid #FFE0A8', borderRadius: 20, backgroundColor: '#FFF8E8', padding: '20px', boxSizing: 'border-box', overflow: 'hidden' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#FF7F2B', fontSize: '0.86rem', fontWeight: 850, marginBottom: 12 }}>
                  <AlertTriangle size={18} color="#FF7F2B" /> {attentionFieldCount > 0 ? `오늘 주의해야 할 밭이 ${attentionFieldCount}개 있어요` : '오늘 주의해야 할 밭이 없어요'}
                </div>

                {attentionFieldCount > 0 ? (
                  <div style={{ position: 'relative', zIndex: 1, display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 14, maxWidth: '68%' }}>
                    {visibleAttentionFields.map(field => {
                      const isDanger = field.dailyStatus === 'DANGER';
                      return (
                        <div key={field.id} style={{
                          display: 'flex', alignItems: 'center', gap: 8,
                          backgroundColor: '#FFFFFF', borderRadius: 12, padding: '10px 12px'
                        }}>
                          <AlertTriangle size={15} color={isDanger ? '#EF4444' : '#FF7F2B'} />
                          <span style={{ fontSize: '0.78rem', fontWeight: 800, color: isDanger ? '#EF4444' : '#FF7F2B' }}>
                            {isDanger ? '위험' : '주의'}
                          </span>
                          <span style={{ fontSize: '0.82rem', fontWeight: 700, color: '#374151' }}>
                            {field.fieldName}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <div style={{ position: 'relative', zIndex: 1, fontSize: '0.85rem', lineHeight: 1.5, color: '#626A65', marginBottom: 14, maxWidth: '68%' }}>
                    현재 특별한 위험은 없어요.
                  </div>
                )}

                <button onClick={() => onTabChange('myfield')} style={{ position: 'relative', zIndex: 1, height: 34, padding: '0 16px', border: '1px solid #FFCFB1', borderRadius: 18, backgroundColor: '#FFFFFF', color: '#FF7D31', fontSize: '0.78rem', fontWeight: 800, cursor: 'pointer' }}>
                  확인하기 →
                </button>

                <img
                  src="/assets/field-alert-mascot.png"
                  alt=""
                  style={{ position: 'absolute', right: 14, bottom: 8, width: 96, height: 96, objectFit: 'contain', pointerEvents: 'none' }}
                />
              </div>
            </div>
          </>
        )}

        {/* Recommended Farming Advice Section */}
        <div style={{ marginBottom: 16 }}>
          <h2 style={{ fontSize: '1.12rem', fontWeight: 900, color: '#191F28', marginBottom: 12, letterSpacing: '-0.02em' }}>
            {isNewUser ? '내 밭에 맞는 추천 농사 정보' : `${shortRegion} 추천 작물 정보`}
          </h2>

          <RecommendedCropCarousel crops={recommendedCrops} />
        </div>

      </div>

      {/* Floating AI Button */}
      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <Bot size={26} color="#FFFFFF" />
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
