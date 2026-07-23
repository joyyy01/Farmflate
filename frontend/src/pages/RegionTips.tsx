import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import type { RegionReport } from '../services/api';

interface RegionTipsProps {
  analysisId: string;
  onNavigate: (page: string, params?: any) => void;
}

export const RegionTipsPage: React.FC<RegionTipsProps> = ({ analysisId, onNavigate }) => {
  const [report, setReport] = useState<RegionReport | null>(null);

  useEffect(() => {
    ApiService.getRegionReport(analysisId)
      .then((data) => setReport(data))
      .catch((err) => console.warn('Failed to load tips', err));
  }, [analysisId]);

  if (!report) return null;

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 p-5 pb-24 max-w-md mx-auto w-full space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <button onClick={() => onNavigate('report', { analysisId })} className="text-slate-400 hover:text-white text-lg">
          ←
        </button>
        <h1 className="text-base font-bold text-white">지역 농사 TIP</h1>
        <button onClick={() => onNavigate('home')} className="text-xs text-slate-400">
          홈으로
        </button>
      </div>

      <div className="text-xs text-slate-400">
        공식 농업자료(농사로 및 흙토람)를 바탕으로 엄선된 실전 농사 가이드입니다.
      </div>

      {/* TIPs List */}
      <div className="space-y-4">
        {report.tips.map((tip) => (
          <div key={tip.tipCode} className="bg-slate-800/90 border border-slate-700 rounded-2xl p-5 space-y-3">
            <div className="flex items-center justify-between">
              <span className="px-2.5 py-0.5 rounded-md text-[11px] font-semibold bg-emerald-500/20 text-emerald-300">
                {tip.sourceName}
              </span>
              <span className="text-xs text-slate-500">TIP #{tip.rank}</span>
            </div>

            <h2 className="text-base font-bold text-white">{tip.title}</h2>
            <p className="text-xs text-slate-300 leading-relaxed">{tip.summary}</p>

            <div className="pt-2 flex justify-between items-center">
              <span className="text-[10px] text-slate-500">기준일: {tip.dataDate}</span>
              <a
                href={tip.sourceUrl}
                target="_blank"
                rel="noreferrer"
                className="px-3 py-1.5 bg-slate-700 hover:bg-slate-600 text-slate-200 font-semibold rounded-lg text-xs transition border border-slate-600 inline-block"
              >
                {tip.actionLabel || '원문 보기'} ➔
              </a>
            </div>
          </div>
        ))}
      </div>

      {/* Legal Footer */}
      <div className="text-[10px] text-slate-500 text-center pt-4 border-t border-slate-800">
        농촌진흥청 국립농업과학원 및 농사로 원자료를 활용한 Farmflate 자체 분석 가이드입니다.
      </div>
    </div>
  );
};
