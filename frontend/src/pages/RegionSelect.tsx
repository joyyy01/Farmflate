import React, { useEffect, useState } from 'react';
import { ApiService } from '../services/api';
import type { RegionDto } from '../services/api';

interface RegionSelectProps {
  onNavigate: (page: string, params?: any) => void;
}

export const RegionSelect: React.FC<RegionSelectProps> = ({ onNavigate }) => {
  const [sidos, setSidos] = useState<RegionDto[]>([]);
  const [sigungus, setSigungus] = useState<RegionDto[]>([]);
  const [selectedSido, setSelectedSido] = useState<string>('');
  const [selectedSigungu, setSelectedSigungu] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string>('');

  useEffect(() => {
    ApiService.getSidos()
      .then((data) => {
        setSidos(data);
        // Default to Jeonbuk if available
        const jeonbuk = data.find(s => s.sidoCode === '52');
        if (jeonbuk) setSelectedSido('52');
      })
      .catch((err) => console.warn('Failed to load sidos', err));
  }, []);

  useEffect(() => {
    if (!selectedSido) {
      setSigungus([]);
      setSelectedSigungu('');
      return;
    }
    ApiService.getSigungus(selectedSido)
      .then((data) => {
        setSigungus(data);
        const gochang = data.find(s => s.sigunguCode === '52180');
        if (gochang) setSelectedSigungu('52180');
        else if (data.length > 0) setSelectedSigungu(data[0].sigunguCode || '');
      })
      .catch((err) => console.warn('Failed to load sigungus', err));
  }, [selectedSido]);

  const handleSidoChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedSido(e.target.value);
    setSelectedSigungu('');
  };

  const handleAnalyze = async () => {
    if (!selectedSido || !selectedSigungu) return;
    setLoading(true);
    setErrorMsg('');

    const idempotencyKey = 'idemp_' + Date.now() + '_' + Math.random().toString(36).substring(2, 9);

    try {
      const res = await ApiService.createRegionAnalysis({
        sidoCode: selectedSido,
        sigunguCode: selectedSigungu,
        forceRefresh: false,
        idempotencyKey
      });

      onNavigate('analyzing', { analysisId: res.analysisId });
    } catch (err: any) {
      setErrorMsg('지역 분석 요청 중 오류가 발생했습니다.');
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col justify-between p-5 max-w-md mx-auto w-full">
      <div>
        {/* Header */}
        <div className="flex items-center gap-3 mb-6">
          <button onClick={() => onNavigate('home')} className="text-slate-400 hover:text-white text-lg">
            ←
          </button>
          <h1 className="text-lg font-bold text-white">지역 선택</h1>
        </div>

        {/* Form Container */}
        <div className="bg-slate-800/90 border border-slate-700 rounded-2xl p-6 shadow-xl space-y-5">
          <div className="text-xs text-slate-400">
            분석을 원하시는 시·도와 시군구를 선택해주세요.
          </div>

          {/* Sido Select */}
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-2">시·도 선택</label>
            <select
              value={selectedSido}
              onChange={handleSidoChange}
              className="w-full bg-slate-700 border border-slate-600 rounded-xl px-4 py-3 text-sm text-white focus:outline-none focus:border-emerald-500"
            >
              <option value="">시·도를 선택하세요</option>
              {sidos.map((s) => (
                <option key={s.sidoCode} value={s.sidoCode}>
                  {s.sidoName}
                </option>
              ))}
            </select>
          </div>

          {/* Sigungu Select */}
          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-2">시군구 선택</label>
            <select
              value={selectedSigungu}
              onChange={(e) => setSelectedSigungu(e.target.value)}
              disabled={!selectedSido}
              className="w-full bg-slate-700 border border-slate-600 rounded-xl px-4 py-3 text-sm text-white focus:outline-none focus:border-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <option value="">시군구를 선택하세요</option>
              {sigungus.map((sg) => (
                <option key={sg.sigunguCode} value={sg.sigunguCode}>
                  {sg.sigunguName}
                </option>
              ))}
            </select>
          </div>

          {errorMsg && (
            <div className="text-xs text-red-400 bg-red-500/10 p-3 rounded-lg border border-red-500/20">
              {errorMsg}
            </div>
          )}

          {/* Disclaimer */}
          <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-3.5 text-xs text-amber-300">
            ℹ️ 이 분석은 선택한 시군구의 대표 기상·토양 자료를 기준으로 제공합니다. 실제 밭의 상태와 다를 수 있습니다.
          </div>

          {/* Submit Button */}
          <button
            onClick={handleAnalyze}
            disabled={!selectedSido || !selectedSigungu || loading}
            className="w-full py-3.5 bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-700 disabled:text-slate-500 text-white font-bold rounded-xl text-sm shadow-xl transition active:scale-95 mt-4"
          >
            {loading ? '분석 시작 중...' : '지역 환경 분석하기'}
          </button>
        </div>
      </div>
    </div>
  );
};
