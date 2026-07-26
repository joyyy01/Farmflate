import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronRight } from 'lucide-react';
import type { RegionReport } from '../../services/api';
import { BackButton } from '../common/BackButton';

interface RecommendedCropsViewProps {
  districtName?: string;
  report?: RegionReport | null;
  onBack: () => void;
  onOpenAIChat: () => void;
  onSelectCrop: (cropName: string) => void;
}

export const RecommendedCropsView: React.FC<RecommendedCropsViewProps> = ({
  districtName,
  report,
  onBack,
  onOpenAIChat: _onOpenAIChat,
  onSelectCrop
}) => {
  const [selectedCategory, setSelectedCategory] = useState<'all' | 'leaf' | 'root' | 'fruit'>('all');

  const crops = (report?.recommendedCrops ?? []).map((rc, index) => ({
    id: rc.cropCode ?? `${rc.rank ?? index}-${rc.cropName ?? 'crop'}`,
    name: rc.cropName ?? '작물명 정보 없음',
    category: rc.category ?? 'unknown',
    score: rc.score,
    desc: rc.positiveReasons[0] ?? rc.cautionReason ?? '근거 자료가 제공되지 않았습니다.',
    icon: rc.iconUrl
  }));

  const filteredCrops = crops.filter(c => selectedCategory === 'all' || c.category === selectedCategory);

  return (
    <div style={{
      width: '100%',
      height: '100%',
      backgroundColor: '#FFFFFF',
      display: 'flex',
      flexDirection: 'column',
      position: 'relative'
    }}>
      <div className="full-screen-view" style={{ padding: '0 20px 96px 20px' }}>
        
        {/* Header */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '30px 1fr 30px',
          alignItems: 'center',
          height: 64,
          borderBottom: '1px solid #ECEFED',
          marginBottom: 16
        }}>
          <BackButton onClick={onBack} style={{ color: '#202A24' }} />
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202A24', margin: 0, textAlign: 'center' }}>
            추천 작물 리스트
          </h1>
          <div />
        </div>

        {/* Title */}
        <div style={{ marginBottom: 20 }}>
          <span style={{ fontSize: '0.8rem', color: '#6F7772', fontWeight: 600 }}>{districtName || '지역 정보 없음'} 분석 기준</span>
          <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#154F36', margin: '2px 0 0 0' }}>
            가장 잘 자라는 작물 TOP {crops.length}
          </h2>
        </div>

        {/* Category Chips */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
          <button
            onClick={() => setSelectedCategory('all')}
            style={{
              padding: '8px 16px', borderRadius: 20, fontSize: '0.82rem', fontWeight: 800,
              backgroundColor: selectedCategory === 'all' ? '#2FA86A' : '#F8FAF8',
              color: selectedCategory === 'all' ? '#FFFFFF' : '#6F7772',
              border: selectedCategory === 'all' ? 'none' : '1px solid #E1E8E4', cursor: 'pointer'
            }}
          >
            전체
          </button>
          <button
            onClick={() => setSelectedCategory('leaf')}
            style={{
              padding: '8px 16px', borderRadius: 20, fontSize: '0.82rem', fontWeight: 800,
              backgroundColor: selectedCategory === 'leaf' ? '#2FA86A' : '#F8FAF8',
              color: selectedCategory === 'leaf' ? '#FFFFFF' : '#6F7772',
              border: selectedCategory === 'leaf' ? 'none' : '1px solid #E1E8E4', cursor: 'pointer'
            }}
          >
            잎채소
          </button>
          <button
            onClick={() => setSelectedCategory('root')}
            style={{
              padding: '8px 16px', borderRadius: 20, fontSize: '0.82rem', fontWeight: 800,
              backgroundColor: selectedCategory === 'root' ? '#2FA86A' : '#F8FAF8',
              color: selectedCategory === 'root' ? '#FFFFFF' : '#6F7772',
              border: selectedCategory === 'root' ? 'none' : '1px solid #E1E8E4', cursor: 'pointer'
            }}
          >
            뿌리채소
          </button>
          <button
            onClick={() => setSelectedCategory('fruit')}
            style={{
              padding: '8px 16px', borderRadius: 20, fontSize: '0.82rem', fontWeight: 800,
              backgroundColor: selectedCategory === 'fruit' ? '#2FA86A' : '#F8FAF8',
              color: selectedCategory === 'fruit' ? '#FFFFFF' : '#6F7772',
              border: selectedCategory === 'fruit' ? 'none' : '1px solid #E1E8E4', cursor: 'pointer'
            }}
          >
            열매채소
          </button>
        </div>

        {/* Dynamic Crop Cards with Pure Vector SVG Icons */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {filteredCrops.length === 0 && <div style={{ backgroundColor: '#F8FAF8', borderRadius: 18, border: '1px solid #E1E8E4', padding: '18px', color: '#6F7772', fontSize: '0.86rem' }}>현재 조건에서 제공된 추천 작물이 없습니다.</div>}
          {filteredCrops.map(c => (
            <motion.div
              key={c.id}
              whileTap={{ scale: 0.98 }}
              onClick={() => c.name !== '작물명 정보 없음' && onSelectCrop(c.name)}
              style={{
                backgroundColor: '#FFFFFF',
                borderRadius: 18,
                border: '1px solid #E1E8E4',
                padding: '16px 18px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                cursor: 'pointer',
                boxShadow: '0 2px 8px rgba(0, 0, 0, 0.02)'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                {c.icon && <img src={c.icon} alt={c.name} style={{ width: 44, height: 44, objectFit: 'contain' }} />}
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: '1rem', fontWeight: 900, color: '#154F36' }}>{c.name}</span>
                    <span style={{ backgroundColor: '#E9F7EC', color: '#2FA86A', fontSize: '0.74rem', fontWeight: 800, padding: '2px 8px', borderRadius: 6 }}>
                      {c.score === null || c.score === undefined ? '자료 부족' : `${c.score}점`}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.78rem', color: '#6F7772', fontWeight: 500, marginTop: 2 }}>
                    {c.desc}
                  </div>
                </div>
              </div>
              <ChevronRight size={20} color="#9CA3AF" />
            </motion.div>
          ))}
        </div>

      </div>
    </div>
  );
};
