import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowLeft, ChevronRight } from 'lucide-react';
import type { RegionReport } from '../../services/api';

interface RecommendedCropsViewProps {
  districtName?: string;
  report?: RegionReport | null;
  onBack: () => void;
  onOpenAIChat: () => void;
  onSelectCrop: (cropName: string) => void;
}

export const RecommendedCropsView: React.FC<RecommendedCropsViewProps> = ({
  districtName = '고창군',
  report,
  onBack,
  onOpenAIChat: _onOpenAIChat,
  onSelectCrop
}) => {
  const [selectedCategory, setSelectedCategory] = useState<'all' | 'leaf' | 'root' | 'fruit'>('all');

  const defaultCrops = [
    { id: 'potato', name: '감자', category: 'root', score: 91, desc: '서늘한 기후 및 배수 우수 토성', icon: '/svg-assets/crops/potato.svg' },
    { id: 'lettuce', name: '상추', category: 'leaf', score: 85, desc: '봄·가을 서늘한 기간이 긺', icon: '/svg-assets/crops/lettuce.svg' },
    { id: 'pear', name: '배', category: 'fruit', score: 78, desc: '일조량 풍부 및 큰 일교차', icon: '/svg-assets/crops/pear.svg' },
    { id: 'cabbage', name: '배추', category: 'leaf', score: 84, desc: '토양 유기물 함량 적정', icon: '/svg-assets/crops/cabbage.svg' },
    { id: 'tomato', name: '토마토', category: 'fruit', score: 80, desc: '시설 및 노지 배수 양호', icon: '/svg-assets/crops/tomato.svg' },
    { id: 'pepper', name: '고추', category: 'fruit', score: 82, desc: '여름철 볕과 지온 유지 유리', icon: '/svg-assets/crops/pepper.svg' }
  ];

  const crops = report?.recommendedCrops && report.recommendedCrops.length > 0 ? report.recommendedCrops.map(rc => {
    const isRoot = rc.cropName.includes('감자') || rc.cropName.includes('당근') || rc.cropName.includes('고구마');
    const isLeaf = rc.cropName.includes('상추') || rc.cropName.includes('배추') || rc.cropName.includes('시금치');
    const cat = isRoot ? 'root' : isLeaf ? 'leaf' : 'fruit';
    const icon = isRoot ? '/svg-assets/crops/potato.svg' : isLeaf ? '/svg-assets/crops/lettuce.svg' : '/svg-assets/crops/pear.svg';
    return {
      id: rc.cropCode.toLowerCase(),
      name: rc.cropName,
      category: cat,
      score: rc.score,
      desc: rc.positiveReasons && rc.positiveReasons.length > 0 ? rc.positiveReasons[0] : '지역 환경 적합도가 높습니다.',
      icon
    };
  }) : defaultCrops;

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
          <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#202A24', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202A24', margin: 0, textAlign: 'center' }}>
            추천 작물 리스트
          </h1>
          <div />
        </div>

        {/* Title */}
        <div style={{ marginBottom: 20 }}>
          <span style={{ fontSize: '0.8rem', color: '#6F7772', fontWeight: 600 }}>{districtName} 분석 기준</span>
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
          {filteredCrops.map(c => (
            <motion.div
              key={c.id}
              whileTap={{ scale: 0.98 }}
              onClick={() => onSelectCrop(c.name)}
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
                <img src={c.icon} alt={c.name} style={{ width: 44, height: 44, objectFit: 'contain' }} />
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: '1rem', fontWeight: 900, color: '#154F36' }}>{c.name}</span>
                    <span style={{ backgroundColor: '#E9F7EC', color: '#2FA86A', fontSize: '0.74rem', fontWeight: 800, padding: '2px 8px', borderRadius: 6 }}>
                      {c.score}점
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
