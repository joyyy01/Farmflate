import React from 'react';
import { ArrowLeft, CheckCircle2, AlertTriangle } from 'lucide-react';

export interface RegionReportSummaryViewProps {
  regionName: string;
  onNext: () => void;
  onOpenAIChat: () => void;
}

export const RegionReportSummaryView: React.FC<RegionReportSummaryViewProps> = ({
  regionName,
  onNext,
  onOpenAIChat: _onOpenAIChat
}) => {
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
          <button onClick={onNext} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#202A24', padding: 0 }}>
            <ArrowLeft size={22} />
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#202A24', margin: 0, textAlign: 'center' }}>
            지역 환경 리포트
          </h1>
          <div />
        </div>

        {/* Region Name Badge & Cheer Mascot */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <div>
            <span style={{ fontSize: '0.8rem', color: '#6F7772', fontWeight: 600 }}>분석 대상 지역</span>
            <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#154F36', margin: '2px 0 0 0' }}>
              {regionName}
            </h2>
          </div>
          <img
            src="/svg-assets/brand/mascot/cheer.svg"
            alt="응원 마스코트"
            style={{ width: 68, height: 68, objectFit: 'contain' }}
          />
        </div>

        {/* Donut Gauge Ring */}
        <div style={{
          backgroundColor: '#F5F8F6',
          borderRadius: 20,
          padding: '20px 16px',
          marginBottom: 24,
          border: '1px solid #E1E8E4',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center'
        }}>
          <span style={{ fontSize: '0.82rem', fontWeight: 700, color: '#6F7772', marginBottom: 12 }}>
            지역 농사 환경 점수
          </span>

          <div style={{
            position: 'relative',
            width: 150,
            height: 150,
            borderRadius: '50%',
            background: 'conic-gradient(from -18deg, #88dc63 0 34%, #61d28c 34% 64%, #66cfe0 64% 83%, #7bdc70 83% 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 14
          }}>
            <div style={{
              width: 124,
              height: 124,
              borderRadius: '50%',
              backgroundColor: '#FFFFFF',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <span style={{ fontSize: '3rem', fontWeight: 900, color: '#145B39', lineHeight: 1 }}>82</span>
              <small style={{ color: '#848985', fontSize: '0.82rem', fontWeight: 500, marginTop: 2 }}>/100</small>
            </div>
          </div>

          <p style={{
            fontSize: '0.86rem',
            color: '#202A24',
            fontWeight: 650,
            textAlign: 'center',
            lineHeight: 1.55,
            margin: 0
          }}>
            고창은 사계절이 뚜렷하고 배수가 양호한 편이에요.<br />다만 장마철엔 집중호우 대비가 필요해요.
          </p>
        </div>

        {/* 4 Category Status Chips with Pure Vector SVG Category Icons */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
          <div style={{ border: '1px solid #E1E8E4', borderRadius: 14, padding: '12px 14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/svg-assets/report/category/climate.svg" alt="기후" style={{ width: 38, height: 38, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.72rem', color: '#6F7772' }}>기후 환경</div>
              <div style={{ fontSize: '0.88rem', fontWeight: 850, color: '#2FA86A', marginTop: 2 }}>양호</div>
            </div>
          </div>

          <div style={{ border: '1px solid #E1E8E4', borderRadius: 14, padding: '12px 14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/svg-assets/report/category/soil.svg" alt="토양" style={{ width: 38, height: 38, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.72rem', color: '#6F7772' }}>토양 환경</div>
              <div style={{ fontSize: '0.88rem', fontWeight: 850, color: '#2FA86A', marginTop: 2 }}>양호</div>
            </div>
          </div>

          <div style={{ border: '1px solid #FFD9B7', borderRadius: 14, padding: '12px 14px', background: 'linear-gradient(135deg, #fffdfa, #fff8f0)', display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/svg-assets/report/category/warning.svg" alt="자연재해" style={{ width: 38, height: 38, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.72rem', color: '#6F7772' }}>자연재해</div>
              <div style={{ fontSize: '0.88rem', fontWeight: 850, color: '#FF842F', marginTop: 2 }}>주의</div>
            </div>
          </div>

          <div style={{ border: '1px solid #E1E8E4', borderRadius: 14, padding: '12px 14px', backgroundColor: '#FFFFFF', display: 'flex', alignItems: 'center', gap: 10 }}>
            <img src="/svg-assets/report/category/greenhouse.svg" alt="재배 환경" style={{ width: 38, height: 38, objectFit: 'contain' }} />
            <div>
              <div style={{ fontSize: '0.72rem', color: '#6F7772' }}>재배 환경</div>
              <div style={{ fontSize: '0.88rem', fontWeight: 850, color: '#2FA86A', marginTop: 2 }}>양호</div>
            </div>
          </div>
        </div>

        {/* Environmental Features Checklist */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '1.02rem', fontWeight: 850, color: '#202A24', marginBottom: 12 }}>
            환경 특징
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ backgroundColor: '#F8FAF8', borderRadius: 12, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <CheckCircle2 size={18} color="#2FA86A" />
              <span style={{ fontSize: '0.84rem', fontWeight: 700, color: '#202A24' }}>황토 성분이 많아 배수가 우수한 편이에요.</span>
            </div>
            <div style={{ backgroundColor: '#F8FAF8', borderRadius: 12, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <CheckCircle2 size={18} color="#2FA86A" />
              <span style={{ fontSize: '0.84rem', fontWeight: 700, color: '#202A24' }}>서해안 인접으로 일조량이 풍부해요.</span>
            </div>
            <div style={{ backgroundColor: '#F8FAF8', borderRadius: 12, padding: '12px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
              <AlertTriangle size={18} color="#FF842F" />
              <span style={{ fontSize: '0.84rem', fontWeight: 700, color: '#202A24' }}>봄철 일교차가 커서 저온 대비가 필요해요.</span>
            </div>
          </div>
        </div>

        {/* Recommended Crops TOP3 with Pure Vector SVG Crop Icons */}
        <div style={{ marginBottom: 24 }}>
          <h3 style={{ fontSize: '1.02rem', fontWeight: 850, color: '#202A24', marginBottom: 12 }}>
            추천 작물 TOP 3
          </h3>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {/* Top 1 */}
            <div style={{
              backgroundColor: '#FFFFFF', borderRadius: 16, border: '1.5px solid #2FA86A',
              padding: '14px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <img src="/svg-assets/crops/potato.svg" alt="감자" style={{ width: 40, height: 40, objectFit: 'contain' }} />
                <div>
                  <div style={{ fontSize: '0.94rem', fontWeight: 900, color: '#154F36' }}>1위. 감자</div>
                  <div style={{ fontSize: '0.76rem', color: '#6F7772', fontWeight: 500 }}>서늘한 기후 · 배수 우수 토성</div>
                </div>
              </div>
              <span style={{ backgroundColor: '#E9F7EC', color: '#2FA86A', padding: '6px 12px', borderRadius: 12, fontSize: '0.86rem', fontWeight: 900 }}>91점</span>
            </div>

            {/* Top 2 */}
            <div style={{
              backgroundColor: '#FFFFFF', borderRadius: 16, border: '1px solid #E1E8E4',
              padding: '14px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <img src="/svg-assets/crops/lettuce.svg" alt="상추" style={{ width: 40, height: 40, objectFit: 'contain' }} />
                <div>
                  <div style={{ fontSize: '0.94rem', fontWeight: 900, color: '#202A24' }}>2위. 상추</div>
                  <div style={{ fontSize: '0.76rem', color: '#6F7772', fontWeight: 500 }}>봄·가을 서늘한 생육 기간 적합</div>
                </div>
              </div>
              <span style={{ backgroundColor: '#F8FAF8', color: '#202A24', padding: '6px 12px', borderRadius: 12, fontSize: '0.86rem', fontWeight: 800 }}>85점</span>
            </div>

            {/* Top 3 */}
            <div style={{
              backgroundColor: '#FFFFFF', borderRadius: 16, border: '1px solid #E1E8E4',
              padding: '14px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <img src="/svg-assets/crops/pear.svg" alt="배" style={{ width: 40, height: 40, objectFit: 'contain' }} />
                <div>
                  <div style={{ fontSize: '0.94rem', fontWeight: 900, color: '#202A24' }}>3위. 배</div>
                  <div style={{ fontSize: '0.76rem', color: '#6F7772', fontWeight: 500 }}>일조량 풍부 및 일교차 적합</div>
                </div>
              </div>
              <span style={{ backgroundColor: '#F8FAF8', color: '#202A24', padding: '6px 12px', borderRadius: 12, fontSize: '0.86rem', fontWeight: 800 }}>78점</span>
            </div>
          </div>
        </div>

      </div>

      {/* CTA Button */}
      <div style={{ position: 'fixed', bottom: 20, left: 0, right: 0, width: '100%', maxWidth: 480, margin: '0 auto', padding: '0 20px', zIndex: 20 }}>
        <button
          onClick={onNext}
          className="btn-farm-primary"
        >
          다음
        </button>
      </div>
    </div>
  );
};
