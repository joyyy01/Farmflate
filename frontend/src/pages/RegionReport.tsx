import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import type { RegionReport } from '../services/api';

interface RegionReportProps {
  analysisId: string;
  onNavigate: (page: string, params?: any) => void;
}

export const RegionReportPage: React.FC<RegionReportProps> = ({ analysisId, onNavigate }) => {
  const [report, setReport] = useState<RegionReport | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    ApiService.getRegionReport(analysisId)
      .then((data) => setReport(data))
      .catch((err) => console.warn('Report fetch error', err))
      .finally(() => setLoading(false));
  }, [analysisId]);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-900 text-slate-100 flex items-center justify-center p-5">
        <div className="text-sm text-slate-400">리포트를 불러오는 중...</div>
      </div>
    );
  }

  if (!report) {
    return (
      <div className="min-h-screen bg-slate-900 text-slate-100 p-5 max-w-md mx-auto">
        <p className="text-red-400 text-sm">리포트를 불러올 수 없습니다.</p>
        <button onClick={() => onNavigate('select')} className="mt-4 px-4 py-2 bg-slate-700 rounded-xl text-xs">
          지역 다시 선택
        </button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 p-5 pb-24 max-w-md mx-auto w-full space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <button onClick={() => onNavigate('select')} className="text-slate-400 hover:text-white text-lg">
          ←
        </button>
        <h1 className="text-base font-bold text-white">지역 환경 리포트</h1>
        <button onClick={() => onNavigate('home')} className="text-slate-400 hover:text-white text-xs">
          홈으로
        </button>
      </div>

      {/* Region Title */}
      <div className="text-center space-y-1">
        <span className="text-xs font-semibold text-emerald-400 uppercase tracking-widest">
          {report.region.sidoName} {report.region.sigunguName}
        </span>
        <h2 className="text-xl font-extrabold text-white">농사 환경 종합 점수</h2>
      </div>

      {/* Main Score Gauge Card */}
      <div className="bg-gradient-to-br from-emerald-950 via-slate-800 to-slate-900 border border-emerald-500/30 rounded-3xl p-6 shadow-2xl text-center relative overflow-hidden">
        <div className="text-5xl font-black text-emerald-400 mb-2">
          {report.regionScore}<span className="text-xl font-normal text-slate-400"> / 100점</span>
        </div>
        <div className="inline-block px-3.5 py-1 bg-emerald-500/20 text-emerald-300 rounded-full text-xs font-bold mb-3">
          {report.regionScore >= 80 ? '농사 환경이 양호한 편이에요' : '일부 조건 확인 필요'}
        </div>
        <p className="text-xs text-slate-300 leading-relaxed max-w-xs mx-auto">
          {report.summary}
        </p>
      </div>

      {/* Confidence Banner */}
      {report.confidence && (
        <div className="bg-slate-800/80 border border-slate-700 rounded-2xl p-4 flex items-center gap-3">
          <span className="text-xl">🛡️</span>
          <div>
            <div className="text-xs font-bold text-slate-200">
              데이터 신뢰도: {report.confidence.grade} ({report.confidence.score}점)
            </div>
            <div className="text-[11px] text-slate-400 mt-0.5">{report.confidence.message}</div>
          </div>
        </div>
      )}

      {/* 4 Environment Component Cards */}
      <div>
        <h3 className="text-sm font-bold text-slate-300 mb-3">4대 환경 요소 분석</h3>
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-slate-800/90 border border-slate-700 rounded-2xl p-4">
            <span className="text-xs text-slate-400">🌡️ 기후 환경</span>
            <div className="text-lg font-bold text-white mt-1">{report.components.climate?.score}점</div>
            <span className="text-[10px] text-emerald-400">{report.components.climate?.grade}</span>
          </div>
          <div className="bg-slate-800/90 border border-slate-700 rounded-2xl p-4">
            <span className="text-xs text-slate-400">🌱 토양 환경</span>
            <div className="text-lg font-bold text-white mt-1">{report.components.soil?.score}점</div>
            <span className="text-[10px] text-emerald-400">{report.components.soil?.grade}</span>
          </div>
          <div className="bg-slate-800/90 border border-slate-700 rounded-2xl p-4">
            <span className="text-xs text-slate-400">⚡ 자연재해 위험</span>
            <div className="text-lg font-bold text-white mt-1">{report.components.hazard?.safetyScore}점</div>
            <span className="text-[10px] text-emerald-400">{report.components.hazard?.grade}</span>
          </div>
          <div className="bg-slate-800/90 border border-slate-700 rounded-2xl p-4">
            <span className="text-xs text-slate-400">🚜 재배 환경</span>
            <div className="text-lg font-bold text-white mt-1">{report.components.cultivation?.score}점</div>
            <span className="text-[10px] text-emerald-400">{report.components.cultivation?.grade}</span>
          </div>
        </div>
      </div>

      {/* Recommended Crops TOP 3 */}
      <div>
        <h3 className="text-sm font-bold text-slate-300 mb-3 flex items-center justify-between">
          <span>🏆 상대적 적합 작물 TOP 3</span>
          <span className="text-[11px] font-normal text-slate-500">지원작물 5종 비교</span>
        </h3>

        <div className="space-y-3">
          {report.recommendedCrops.map((crop) => (
            <div key={crop.cropCode} className="bg-slate-800/90 border border-slate-700 rounded-2xl p-4 space-y-2">
              <div className="flex justify-between items-center">
                <div className="flex items-center gap-2">
                  <span className="w-5 h-5 bg-emerald-500/20 text-emerald-400 rounded-full flex items-center justify-center font-bold text-xs">
                    {crop.rank}
                  </span>
                  <span className="text-base font-bold text-white">{crop.cropName}</span>
                </div>
                <span className="text-xs font-extrabold text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-lg">
                  {crop.score}점
                </span>
              </div>

              {crop.positiveReasons?.map((reason, idx) => (
                <div key={idx} className="text-xs text-slate-300 flex items-start gap-1.5">
                  <span className="text-emerald-400">✓</span>
                  <span>{reason}</span>
                </div>
              ))}

              {crop.cautionReason && (
                <div className="text-xs text-amber-300/90 bg-amber-500/10 p-2.5 rounded-lg border border-amber-500/20 mt-1">
                  ⚠️ {crop.cautionReason}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Action Buttons: Navigate to Risks & TIPs */}
      <div className="grid grid-cols-2 gap-3 pt-2">
        <button
          onClick={() => onNavigate('risks', { analysisId })}
          className="py-3.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 font-bold rounded-2xl text-xs transition"
        >
          🚨 핵심 환경 위험 ({report.topRisks.length}건)
        </button>
        <button
          onClick={() => onNavigate('tips', { analysisId })}
          className="py-3.5 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-2xl text-xs transition shadow-lg"
        >
          💡 지역 농사 TIP ({report.tips.length}건)
        </button>
      </div>
    </div>
  );
};
