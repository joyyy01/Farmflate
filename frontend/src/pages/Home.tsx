import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import type { HomeData } from '../services/api';

interface HomeProps {
  onNavigate: (page: string, params?: any) => void;
  onOpenAiFab: () => void;
}

export const Home: React.FC<HomeProps> = ({ onNavigate, onOpenAiFab }) => {
  const [data, setData] = useState<HomeData | null>(null);
  const [, setLoading] = useState(true);

  useEffect(() => {
    ApiService.getHome()
      .then((res) => setData(res))
      .catch((err) => console.warn('Failed to load home data', err))
      .finally(() => setLoading(false));
  }, []);

  const displayName = data?.user?.displayName || 'Farmflate 사용자';
  const hasAnalysis = !!data?.latestRegionAnalysis;

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col justify-between pb-24">
      {/* Top Header */}
      <header className="p-6 bg-slate-800/80 backdrop-blur-md border-b border-slate-700/50 sticky top-0 z-10 flex justify-between items-center">
        <div>
          <span className="text-xs font-semibold text-emerald-400 uppercase tracking-wider">Farmflate</span>
          <h1 className="text-xl font-bold text-white mt-0.5">안녕하세요, {displayName}님 👋</h1>
        </div>
        <button
          onClick={() => {
            const token = localStorage.getItem('token');
            if (!token) {
              window.location.href = 'https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=a7486d7b636833e20bd6c11cca7e4b24&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Fkakao';
            }
          }}
          className="text-xs bg-slate-700 hover:bg-slate-600 px-3 py-1.5 rounded-lg border border-slate-600 transition"
        >
          {localStorage.getItem('token') ? '로그인됨' : '카카오 로그인'}
        </button>
      </header>

      {/* Main Content */}
      <main className="p-5 space-y-6 max-w-md mx-auto w-full">
        {/* Notice Info Warning Banner */}
        <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-3.5 text-xs text-amber-300 flex items-start gap-2.5">
          <span className="text-base">ℹ️</span>
          <span>이 분석은 선택한 시군구의 대표 기상·토양 자료를 기준으로 제공합니다. 실제 밭의 상태와 다를 수 있습니다.</span>
        </div>

        {/* Today Weather Card */}
        <section className="bg-gradient-to-br from-slate-800 to-slate-800/90 border border-slate-700 rounded-2xl p-5 shadow-xl">
          <h2 className="text-sm font-medium text-slate-400 mb-3 flex items-center gap-2">
            <span>🌦️</span> 오늘 날씨
          </h2>
          {data?.weather && data.weather.status === 'AVAILABLE' ? (
            <div className="flex items-center justify-between">
              <div>
                <div className="text-3xl font-extrabold text-white">{data.weather.temperature}°C</div>
                <div className="text-xs text-slate-400 mt-1">
                  최저 {data.weather.minTemperature}°C / 최고 {data.weather.maxTemperature}°C
                </div>
              </div>
              <div className="text-right">
                <span className="inline-block px-3 py-1 bg-blue-500/20 text-blue-300 rounded-full text-xs font-semibold">
                  강수확률 {data.weather.precipitationProbability}%
                </span>
              </div>
            </div>
          ) : (
            <div className="text-center py-4 text-slate-400 text-sm">
              <p className="font-semibold text-slate-300">날씨 데이터가 없어요</p>
              <p className="text-xs text-slate-500 mt-1">지역을 선택하면 오늘 날씨를 확인할 수 있어요.</p>
            </div>
          )}
        </section>

        {/* Today Action Card */}
        <section className="bg-slate-800/90 border border-slate-700 rounded-2xl p-5 shadow-xl">
          <h2 className="text-sm font-medium text-slate-400 mb-2 flex items-center gap-2">
            <span>📋</span> 오늘 해야 할 일
          </h2>
          {data?.todayAction ? (
            <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-4 mt-2">
              <div className="font-semibold text-emerald-300 text-sm">{data.todayAction.title}</div>
              <div className="text-xs text-slate-400 mt-1">{data.todayAction.reason}</div>
            </div>
          ) : (
            <div className="text-center py-4 text-slate-400 text-sm">
              <p className="font-semibold text-slate-300">오늘 해야 할 일이 없어요</p>
              <p className="text-xs text-slate-500 mt-1">지역 환경 분석 후 주요 조치사항을 알려드릴게요.</p>
            </div>
          )}
        </section>

        {/* Region Analysis CTA */}
        <section className="bg-gradient-to-r from-emerald-600 to-teal-600 rounded-2xl p-6 shadow-2xl text-white relative overflow-hidden">
          <div className="relative z-10">
            <h2 className="text-lg font-extrabold mb-1">
              {hasAnalysis ? '최근 지역 분석 정보' : '내 지역의 농사 환경 알아보기'}
            </h2>
            <p className="text-xs text-emerald-100 mb-4 opacity-90">
              {hasAnalysis
                ? `${data.latestRegionAnalysis?.regionName} (종합 ${data.latestRegionAnalysis?.score}점)`
                : '기상청 및 농촌진흥청 데이터로 시군구 환경점수를 확인해보세요.'}
            </p>
            <button
              onClick={() => {
                if (hasAnalysis) {
                  onNavigate('report', { analysisId: data.latestRegionAnalysis?.analysisId });
                } else {
                  onNavigate('select');
                }
              }}
              className="w-full py-3 bg-white text-emerald-800 font-bold rounded-xl text-sm shadow-lg hover:bg-emerald-50 transition active:scale-95"
            >
              {hasAnalysis ? '지역 환경 리포트 보기 ➔' : '지역 환경 분석하기 ➔'}
            </button>
            {hasAnalysis && (
              <button
                onClick={() => onNavigate('select')}
                className="w-full mt-2.5 py-2.5 bg-emerald-700/50 hover:bg-emerald-700 text-white font-medium rounded-xl text-xs transition"
              >
                다른 지역 다시 분석하기
              </button>
            )}
          </div>
        </section>

        {/* Recommended Farming Info */}
        <section className="bg-slate-800/90 border border-slate-700 rounded-2xl p-5 shadow-xl">
          <h2 className="text-sm font-medium text-slate-400 mb-3 flex items-center gap-2">
            <span>🌱</span> 추천 농사 정보
          </h2>
          {hasAnalysis && data.latestRegionAnalysis?.topCrop ? (
            <div className="bg-slate-700/50 rounded-xl p-4 border border-slate-600">
              <div className="flex justify-between items-center mb-2">
                <span className="text-base font-bold text-emerald-400">
                  TOP 1 추천: {data.latestRegionAnalysis.topCrop.cropName}
                </span>
                <span className="text-xs font-semibold px-2 py-0.5 bg-emerald-500/20 text-emerald-300 rounded">
                  적합도 {data.latestRegionAnalysis.topCrop.score}점
                </span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                {data.latestRegionAnalysis.topCrop.reason}
              </p>
            </div>
          ) : (
            <div className="text-center py-4 text-slate-400 text-sm">
              <p className="font-semibold text-slate-300">아직 추천 정보가 없어요</p>
              <p className="text-xs text-slate-500 mt-1">지역을 분석하면 추천 작물과 주의사항을 확인할 수 있어요.</p>
            </div>
          )}
        </section>
      </main>

      {/* Floating AI FAB */}
      <button
        onClick={onOpenAiFab}
        className="fixed bottom-20 right-5 bg-gradient-to-r from-purple-600 to-indigo-600 text-white p-3.5 rounded-full shadow-2xl hover:scale-105 transition-all active:scale-95 border border-purple-400/40 flex items-center gap-2 text-xs font-bold z-20"
      >
        <span className="text-base">🤖</span> AI 도우미
      </button>

      {/* Bottom Navigation */}
      <nav className="fixed bottom-0 left-0 right-0 bg-slate-800/95 backdrop-blur-lg border-t border-slate-700 p-3 flex justify-around items-center max-w-md mx-auto z-10">
        <button onClick={() => onNavigate('home')} className="flex flex-col items-center text-emerald-400">
          <span className="text-lg">🏠</span>
          <span className="text-[10px] font-semibold mt-0.5">홈</span>
        </button>
        <button onClick={() => onNavigate('select')} className="flex flex-col items-center text-slate-400 hover:text-slate-200">
          <span className="text-lg">🗺️</span>
          <span className="text-[10px] font-semibold mt-0.5">지역 탐색</span>
        </button>
        <button onClick={() => alert('마이팜 등록은 다음 구현 범위입니다.')} className="flex flex-col items-center text-slate-400 hover:text-slate-200">
          <span className="text-lg">🌾</span>
          <span className="text-[10px] font-semibold mt-0.5">마이팜</span>
        </button>
      </nav>
    </div>
  );
};
