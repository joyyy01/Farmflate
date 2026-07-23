import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';

interface RegionAnalyzingProps {
  analysisId: string;
  onNavigate: (page: string, params?: any) => void;
}

const STEPS = [
  '선택한 지역을 확인하고 있어요.',
  '최근 기상 자료를 확인하고 있어요.',
  '앞으로 3일의 날씨를 확인하고 있어요.',
  '지역 대표 토양 자료를 확인하고 있어요.',
  '지원 작물 5종을 비교하고 있어요.',
  '지역 환경 리포트를 만들고 있어요.'
];

export const RegionAnalyzing: React.FC<RegionAnalyzingProps> = ({ analysisId, onNavigate }) => {
  const [currentStepIndex, setCurrentStepIndex] = useState<number>(0);
  const [isSlow, setIsSlow] = useState<boolean>(false);

  useEffect(() => {
    // Step progression animation timer
    const stepInterval = setInterval(() => {
      setCurrentStepIndex((prev) => {
        if (prev < STEPS.length - 1) return prev + 1;
        return prev;
      });
    }, 600);

    // 8 second timeout warning timer
    const slowTimeout = setTimeout(() => {
      setIsSlow(true);
    }, 8000);

    // Polling analysis status
    const pollInterval = setInterval(() => {
      ApiService.getAnalysisStatus(analysisId)
        .then((res) => {
          if (res.status === 'COMPLETED') {
            clearInterval(stepInterval);
            clearInterval(pollInterval);
            onNavigate('report', { analysisId });
          }
        })
        .catch((err) => console.warn('Status poll error', err));
    }, 1000);

    return () => {
      clearInterval(stepInterval);
      clearInterval(pollInterval);
      clearTimeout(slowTimeout);
    };
  }, [analysisId, onNavigate]);

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col justify-center items-center p-6 max-w-md mx-auto w-full">
      <div className="bg-slate-800/90 border border-slate-700 rounded-3xl p-8 shadow-2xl w-full text-center space-y-6">
        {/* Loading Spinner */}
        <div className="relative w-20 h-20 mx-auto">
          <div className="absolute inset-0 rounded-full border-4 border-emerald-500/20 animate-ping"></div>
          <div className="w-20 h-20 rounded-full border-4 border-emerald-500 border-t-transparent animate-spin"></div>
        </div>

        <div>
          <h2 className="text-lg font-extrabold text-white mb-1">지역 환경 분석 중</h2>
          <p className="text-xs text-slate-400">공공데이터 기반으로 환경 점수를 산출하고 있어요.</p>
        </div>

        {/* Steps Checklist */}
        <div className="space-y-3 text-left bg-slate-900/60 rounded-2xl p-4 border border-slate-700/50">
          {STEPS.map((step, idx) => {
            const isDone = idx <= currentStepIndex;
            const isCurrent = idx === currentStepIndex;
            return (
              <div key={idx} className="flex items-center gap-3 text-xs">
                <span className={`w-5 h-5 rounded-full flex items-center justify-center font-bold text-[10px] ${
                  isDone ? 'bg-emerald-500 text-slate-950' : 'bg-slate-700 text-slate-400'
                }`}>
                  {isDone ? '✓' : idx + 1}
                </span>
                <span className={isCurrent ? 'text-emerald-400 font-semibold animate-pulse' : isDone ? 'text-slate-200' : 'text-slate-500'}>
                  {step}
                </span>
              </div>
            );
          })}
        </div>

        {/* Slow warning message if > 8 seconds */}
        {isSlow && (
          <div className="text-xs text-amber-300 bg-amber-500/10 p-3 rounded-xl border border-amber-500/30 animate-fade-in">
            ⏱️ 공공데이터를 불러오는 데 시간이 걸리고 있어요. 잠시만 기다려주세요...
          </div>
        )}
      </div>
    </div>
  );
};
