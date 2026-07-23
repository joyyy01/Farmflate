import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import type { RegionReport } from '../services/api';

interface RegionRisksProps {
  analysisId: string;
  onNavigate: (page: string, params?: any) => void;
}

export const RegionRisksPage: React.FC<RegionRisksProps> = ({ analysisId, onNavigate }) => {
  const [report, setReport] = useState<RegionReport | null>(null);

  useEffect(() => {
    ApiService.getRegionReport(analysisId)
      .then((data) => setReport(data))
      .catch((err) => console.warn('Failed to load risks', err));
  }, [analysisId]);

  if (!report) return null;

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 p-5 pb-24 max-w-md mx-auto w-full space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <button onClick={() => onNavigate('report', { analysisId })} className="text-slate-400 hover:text-white text-lg">
          ←
        </button>
        <h1 className="text-base font-bold text-white">지역 핵심 환경 위험</h1>
        <button onClick={() => onNavigate('tips', { analysisId })} className="text-xs text-emerald-400 font-semibold">
          농사 TIP ➔
        </button>
      </div>

      <div className="text-xs text-slate-400">
        선택 지역({report.region.sidoName} {report.region.sigunguName})에서 현재 주의해야 할 핵심 위험 요소입니다.
      </div>

      {/* Risks List */}
      <div className="space-y-4">
        {report.topRisks.length === 0 ? (
          <div className="bg-slate-800 rounded-2xl p-6 text-center text-slate-400 text-xs">
            현재 감지된 특별한 환경 위험이 없습니다. 🌿
          </div>
        ) : (
          report.topRisks.map((risk) => (
            <div key={risk.riskCode} className="bg-slate-800/90 border border-slate-700 rounded-2xl p-5 space-y-3">
              <div className="flex items-center justify-between">
                <span className={`px-2.5 py-1 rounded-lg text-xs font-bold ${
                  risk.level === 'DANGER' ? 'bg-red-500/20 text-red-400 border border-red-500/30' : 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                }`}>
                  {risk.level === 'DANGER' ? '🚨 DANGER' : '⚠️ CAUTION'}
                </span>
                <span className="text-xs text-slate-400">순위 #{risk.rank}</span>
              </div>

              <h2 className="text-base font-bold text-white">{risk.title}</h2>
              <p className="text-xs text-slate-300 leading-relaxed">{risk.description}</p>

              {/* Actions */}
              {risk.actions && risk.actions.length > 0 && (
                <div className="bg-slate-900/60 p-3 rounded-xl border border-slate-700/50 space-y-1">
                  <div className="text-[11px] font-bold text-emerald-400">권장 대응 조치</div>
                  {risk.actions.map((act, idx) => (
                    <div key={idx} className="text-xs text-slate-300">
                      • {act}
                    </div>
                  ))}
                </div>
              )}

              {/* Affected crops */}
              {risk.affectedCrops && risk.affectedCrops.length > 0 && (
                <div className="text-xs text-slate-400 flex items-center gap-1">
                  <span>영향 작물:</span>
                  <span className="text-slate-200 font-semibold">{risk.affectedCrops.join(', ')}</span>
                </div>
              )}

              {/* Source attribution */}
              {risk.source && (
                <div className="text-[10px] text-slate-500 border-t border-slate-700/50 pt-2 flex justify-between">
                  <span>출처: {risk.source.provider} ({risk.source.service})</span>
                  <a href={risk.source.sourceUrl} target="_blank" rel="noreferrer" className="underline hover:text-slate-400">
                    원문 보기
                  </a>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  );
};
