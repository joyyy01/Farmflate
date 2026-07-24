import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronRight, HelpCircle, X, Check, Bookmark, MessageSquare, Send, ShieldCheck, Mail, Calendar } from 'lucide-react';
import type { TabState, CommunityPost } from '../../types/farmflate';
import { BottomNavigation } from '../common/BottomNavigation';
import { CommunityPostDetailModal } from './CommunityPostDetailModal';
import { ApiService } from '../../services/api';

interface MyPageViewProps {
  userName?: string;
  userEmail?: string;
  userRegion?: string;
  posts?: CommunityPost[];
  onOpenAIChat: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
  onLogout?: () => void;
  onGoToExplore?: () => void;
  onToggleLike?: (postId: string) => void;
  onToggleSave?: (postId: string) => void;
  onAddComment?: (postId: string, commentText: string) => void;
}

export const MyPageView: React.FC<MyPageViewProps> = ({
  userName = '사용자님',
  userEmail = 'user@farmflate.com',
  userRegion = '전북 고창군',
  posts = [],
  onOpenAIChat,
  activeTab,
  onTabChange,
  onLogout,
  onGoToExplore,
  onToggleLike = () => {},
  onToggleSave = () => {},
  onAddComment = () => {}
}) => {
  const [activeModal, setActiveModal] = useState<'account' | 'crops' | 'saved' | 'my_posts' | 'support' | null>(null);
  const [selectedPost, setSelectedPost] = useState<CommunityPost | null>(null);

  /* Real Favorite Crops State from LocalStorage */
  const availableCrops = [
    { name: '감자', icon: '/svg-assets/crops/potato.svg' },
    { name: '상추', icon: '/svg-assets/crops/lettuce.svg' },
    { name: '오이', icon: '/svg-assets/crops/cucumber.svg' },
    { name: '사과', icon: '/svg-assets/crops/apple.svg' },
    { name: '배', icon: '/svg-assets/crops/pear.svg' },
    { name: '고추', icon: '/svg-assets/crops/pepper.svg' },
    { name: '토마토', icon: '/svg-assets/crops/tomato.svg' },
    { name: '배추', icon: '/svg-assets/crops/cabbage.svg' }
  ];

  const [favoriteCrops, setFavoriteCrops] = useState<string[]>(() => {
    const saved = localStorage.getItem('farmflate_favorite_crops');
    return saved ? JSON.parse(saved) : ['감자', '상추'];
  });

  const toggleFavoriteCrop = (cropName: string) => {
    setFavoriteCrops(prev => {
      const next = prev.includes(cropName) ? prev.filter(c => c !== cropName) : [...prev, cropName];
      localStorage.setItem('farmflate_favorite_crops', JSON.stringify(next));
      return next;
    });
  };

  /* Support FAQ State & Inquiry */
  const [expandedFaq, setExpandedFaq] = useState<number | null>(0);
  const [inquiryText, setInquiryText] = useState('');
  const [inquirySuccess, setInquirySuccess] = useState(false);
  const [isSubmittingInquiry, setIsSubmittingInquiry] = useState(false);

  const faqs = [
    {
      q: '토양 및 날씨 분석 정보는 어떻게 얻어오나요?',
      a: '농촌진흥청(흙람보) 토양분석 공공 데이터 API 및 기상청 단기예보 실시간 데이터를 연동하여 고창군 등 각 지역 환경을 가중치 엔진으로 분석합니다.'
    },
    {
      q: '추천 작물 점수는 어떤 기준으로 계산되나요?',
      a: '지역 대표 토양 pH, 유기물 함량, 일조시간, 계절 강수확률, 서리/집중호우 위험도를 4대 평가 지표로 합산해 100점 만점으로 시뮬레이션합니다.'
    },
    {
      q: '1:1 문의 응답은 얼마나 걸리나요?',
      a: '영업일 기준 24시간 이내에 가입하신 카카오 이메일 계정으로 상세 답변을 보내드립니다.'
    }
  ];

  const savedPosts = posts.filter(p => p.isSaved);
  const myAuthoredPosts = posts.filter(p => p.author === userName || p.author.includes('초보농부') || p.author.includes('사용자'));

  const handleInquirySubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inquiryText.trim() || isSubmittingInquiry) return;
    
    setIsSubmittingInquiry(true);
    try {
      await ApiService.submitInquiry({ inquiryText: inquiryText.trim() });
      setInquirySuccess(true);
      setInquiryText('');
      setTimeout(() => setInquirySuccess(false), 3500);
    } catch (err) {
      console.warn('Inquiry submit failed:', err);
    } finally {
      setIsSubmittingInquiry(false);
    }
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar" style={{ padding: '32px 20px 96px 20px', overflowY: 'auto' }}>
        
        {/* Top Header */}
        <h2 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#191F28', marginBottom: 20 }}>
          설정
        </h2>

        {/* Profile Card */}
        <div style={{
          backgroundColor: '#EDF7ED',
          borderRadius: 20,
          padding: '20px',
          marginBottom: 24,
          border: '1px solid #D4EDDA',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <div style={{
              width: 50, height: 50, borderRadius: '50%',
              backgroundColor: '#FFFFFF', border: '2px solid #2FA86A',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              overflow: 'hidden'
            }}>
              <img
                src="/svg-assets/brand/mascot/hello.svg"
                alt="사용자 프로필"
                style={{ width: 42, height: 42, objectFit: 'contain' }}
              />
            </div>
            <div>
              <h3 style={{ fontSize: '1.15rem', fontWeight: 900, color: '#191F28', margin: '0 0 2px 0' }}>
                {userName.endsWith('님') ? userName : `${userName}님`}
              </h3>
              <p style={{ fontSize: '0.82rem', color: '#6F7772', fontWeight: 600, margin: 0 }}>
                {userRegion}
              </p>
            </div>
          </div>

          <img
            src="/svg-assets/brand/mascot/laugh.svg"
            alt="웃는 마스코트"
            style={{ width: 48, height: 48, objectFit: 'contain' }}
          />
        </div>

        {/* Category 1: 계정 */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: '0.82rem', fontWeight: 800, color: '#2FA86A', marginBottom: 8, paddingLeft: 4 }}>
            계정
          </div>
          <div style={{ backgroundColor: '#FFFFFF', borderRadius: 18, border: '1px solid #E5E8EB', overflow: 'hidden' }}>
            <div onClick={() => setActiveModal('account')} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/ui-icons/user.svg" alt="" style={{ width: 18, height: 18 }} /> 계정 정보
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>

            <div onClick={onGoToExplore} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/ui-icons/location.svg" alt="" style={{ width: 18, height: 18 }} /> 지역 수정 / 선택
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>

            <div onClick={() => setActiveModal('crops')} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/crops/leaf.svg" alt="" style={{ width: 18, height: 18 }} /> 관심 작물 설정
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: '0.78rem', color: '#2FA86A', fontWeight: 800 }}>{favoriteCrops.join(', ')}</span>
                <ChevronRight size={18} color="#CBD5E1" />
              </div>
            </div>

            <div onClick={onLogout} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#ef4444' }}>
                <img src="/svg-assets/ui-icons/user.svg" alt="" style={{ width: 18, height: 18 }} /> 로그아웃
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>
          </div>
        </div>

        {/* Category 2: 내 활동 */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: '0.82rem', fontWeight: 800, color: '#2FA86A', marginBottom: 8, paddingLeft: 4 }}>
            내 활동
          </div>
          <div style={{ backgroundColor: '#FFFFFF', borderRadius: 18, border: '1px solid #E5E8EB', overflow: 'hidden' }}>
            <div onClick={() => setActiveModal('saved')} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #F1F5F9', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/ui-icons/star.svg" alt="" style={{ width: 18, height: 18 }} /> 저장한 게시글
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 700 }}>{savedPosts.length}개</span>
                <ChevronRight size={18} color="#CBD5E1" />
              </div>
            </div>

            <div onClick={() => setActiveModal('my_posts')} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <img src="/svg-assets/ui-icons/chat.svg" alt="" style={{ width: 18, height: 18 }} /> 작성한 게시글
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 700 }}>{myAuthoredPosts.length}개</span>
                <ChevronRight size={18} color="#CBD5E1" />
              </div>
            </div>
          </div>
        </div>

        {/* Category 3: 지원 */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: '0.82rem', fontWeight: 800, color: '#2FA86A', marginBottom: 8, paddingLeft: 4 }}>
            지원
          </div>
          <div style={{ backgroundColor: '#FFFFFF', borderRadius: 18, border: '1px solid #E5E8EB', overflow: 'hidden' }}>
            <div onClick={() => setActiveModal('support')} style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', cursor: 'pointer' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.9rem', fontWeight: 700, color: '#202a24' }}>
                <HelpCircle size={18} color="#7c847f" /> 고객센터 / FAQ
              </div>
              <ChevronRight size={18} color="#CBD5E1" />
            </div>
          </div>
        </div>

      </div>

      {/* 1. Modal: 계정 정보 */}
      <AnimatePresence>
        {activeModal === 'account' && (
          <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }} onClick={() => setActiveModal(null)}>
            <motion.div initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }} transition={{ type: 'spring', damping: 25 }} onClick={e => e.stopPropagation()} style={{ width: '100%', maxWidth: 480, backgroundColor: '#FFFFFF', borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: 24 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                <h3 style={{ fontSize: '1.15rem', fontWeight: 900, color: '#191F28', margin: 0 }}>계정 정보</h3>
                <button onClick={() => setActiveModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}><X size={20} /></button>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 24 }}>
                <div style={{ backgroundColor: '#F8FAF8', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: '0.86rem', color: '#6E7671', fontWeight: 600 }}><ShieldCheck size={18} color="#2FA86A" /> 가입 유형</div>
                  <span style={{ backgroundColor: '#E9F7EC', color: '#2FA86A', fontSize: '0.78rem', fontWeight: 850, padding: '4px 10px', borderRadius: 10 }}>카카오 간편인증 계정</span>
                </div>
                <div style={{ backgroundColor: '#F8FAF8', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: '0.86rem', color: '#6E7671', fontWeight: 600 }}><Mail size={18} color="#2FA86A" /> 계정 이메일</div>
                  <span style={{ fontSize: '0.86rem', fontWeight: 800, color: '#191F28' }}>{userEmail}</span>
                </div>
                <div style={{ backgroundColor: '#F8FAF8', borderRadius: 16, padding: '14px 16px', border: '1px solid #EAEFEA', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: '0.86rem', color: '#6E7671', fontWeight: 600 }}><Calendar size={18} color="#2FA86A" /> 분석 등록 지역</div>
                  <span style={{ fontSize: '0.86rem', fontWeight: 800, color: '#191F28' }}>{userRegion}</span>
                </div>
              </div>

              <button onClick={() => setActiveModal(null)} className="btn-farm-primary" style={{ width: '100%', height: 48, borderRadius: 14 }}>확인</button>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 2. Modal: 관심 작물 설정 */}
      <AnimatePresence>
        {activeModal === 'crops' && (
          <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }} onClick={() => setActiveModal(null)}>
            <motion.div initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }} transition={{ type: 'spring', damping: 25 }} onClick={e => e.stopPropagation()} style={{ width: '100%', maxWidth: 480, backgroundColor: '#FFFFFF', borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: 24 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h3 style={{ fontSize: '1.15rem', fontWeight: 900, color: '#191F28', margin: 0 }}>관심 작물 설정</h3>
                <button onClick={() => setActiveModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}><X size={20} /></button>
              </div>
              <p style={{ fontSize: '0.82rem', color: '#6E7671', margin: '0 0 20px 0', fontWeight: 500 }}>
                관심 작물을 선택하시면 홈 화면과 리포트에 우선 배치됩니다.
              </p>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 24 }}>
                {availableCrops.map(c => {
                  const isChecked = favoriteCrops.includes(c.name);
                  return (
                    <div
                      key={c.name}
                      onClick={() => toggleFavoriteCrop(c.name)}
                      style={{
                        backgroundColor: isChecked ? '#E9F7EC' : '#F8FAF8',
                        border: isChecked ? '1.5px solid #2FA86A' : '1px solid #EAEFEA',
                        borderRadius: 16, padding: '12px 14px',
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                        cursor: 'pointer', transition: 'all 0.15s ease'
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <img src={c.icon} alt={c.name} style={{ width: 28, height: 28, objectFit: 'contain' }} />
                        <span style={{ fontSize: '0.88rem', fontWeight: 800, color: isChecked ? '#154F36' : '#191F28' }}>{c.name}</span>
                      </div>
                      {isChecked && <div style={{ width: 22, height: 22, borderRadius: '50%', backgroundColor: '#2FA86A', color: '#FFF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Check size={14} /></div>}
                    </div>
                  );
                })}
              </div>

              <button onClick={() => setActiveModal(null)} className="btn-farm-primary" style={{ width: '100%', height: 48, borderRadius: 14 }}>저장하기</button>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 3. Modal: 저장한 게시글 */}
      <AnimatePresence>
        {activeModal === 'saved' && (
          <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }} onClick={() => setActiveModal(null)}>
            <motion.div initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }} transition={{ type: 'spring', damping: 25 }} onClick={e => e.stopPropagation()} style={{ width: '100%', maxWidth: 480, height: '80vh', backgroundColor: '#FFFFFF', borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: 24, display: 'flex', flexDirection: 'column' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
                <h3 style={{ fontSize: '1.15rem', fontWeight: 900, color: '#191F28', margin: 0, display: 'flex', alignItems: 'center', gap: 6 }}>
                  <Bookmark size={20} color="#0284C7" /> 저장한 게시글 ({savedPosts.length})
                </h3>
                <button onClick={() => setActiveModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}><X size={20} /></button>
              </div>

              <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 12 }}>
                {savedPosts.length === 0 ? (
                  <div style={{ padding: '40px 0', textAlign: 'center', color: '#8E9892', fontSize: '0.86rem', fontWeight: 600 }}>
                    아직 북마크 저장한 게시글이 없습니다.
                  </div>
                ) : (
                  savedPosts.map(post => (
                    <div key={post.id} onClick={() => setSelectedPost(post)} style={{ backgroundColor: '#F8FAF8', borderRadius: 16, padding: '16px', border: '1px solid #EAEFEA', cursor: 'pointer' }}>
                      <div style={{ fontSize: '0.74rem', color: '#2FA86A', fontWeight: 800, marginBottom: 4 }}>{post.category} · {post.tagLocation}</div>
                      <h4 style={{ fontSize: '0.94rem', fontWeight: 850, color: '#191F28', margin: '0 0 6px 0' }}>{post.title}</h4>
                      <div style={{ fontSize: '0.76rem', color: '#8E9892', fontWeight: 500 }}>작성자: {post.author} · {post.timeAgo}</div>
                    </div>
                  ))
                )}
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 4. Modal: 작성한 게시글 */}
      <AnimatePresence>
        {activeModal === 'my_posts' && (
          <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }} onClick={() => setActiveModal(null)}>
            <motion.div initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }} transition={{ type: 'spring', damping: 25 }} onClick={e => e.stopPropagation()} style={{ width: '100%', maxWidth: 480, height: '80vh', backgroundColor: '#FFFFFF', borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: 24, display: 'flex', flexDirection: 'column' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
                <h3 style={{ fontSize: '1.15rem', fontWeight: 900, color: '#191F28', margin: 0, display: 'flex', alignItems: 'center', gap: 6 }}>
                  <MessageSquare size={20} color="#2FA86A" /> 작성한 게시글 ({myAuthoredPosts.length})
                </h3>
                <button onClick={() => setActiveModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}><X size={20} /></button>
              </div>

              <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 12 }}>
                {myAuthoredPosts.length === 0 ? (
                  <div style={{ padding: '40px 0', textAlign: 'center', color: '#8E9892', fontSize: '0.86rem', fontWeight: 600 }}>
                    아직 직접 작성한 게시글이 없습니다.
                  </div>
                ) : (
                  myAuthoredPosts.map(post => (
                    <div key={post.id} onClick={() => setSelectedPost(post)} style={{ backgroundColor: '#F8FAF8', borderRadius: 16, padding: '16px', border: '1px solid #EAEFEA', cursor: 'pointer' }}>
                      <div style={{ fontSize: '0.74rem', color: '#2FA86A', fontWeight: 800, marginBottom: 4 }}>{post.category} · {post.tagLocation}</div>
                      <h4 style={{ fontSize: '0.94rem', fontWeight: 850, color: '#191F28', margin: '0 0 6px 0' }}>{post.title}</h4>
                      <div style={{ fontSize: '0.76rem', color: '#8E9892', fontWeight: 500 }}>댓글 {post.comments?.length || post.commentCount}개 · 좋아요 {post.likeCount}개</div>
                    </div>
                  ))
                )}
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 5. Modal: 고객센터 / FAQ / 1:1 문의 */}
      <AnimatePresence>
        {activeModal === 'support' && (
          <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'flex-end', justifyContent: 'center' }} onClick={() => setActiveModal(null)}>
            <motion.div initial={{ y: '100%' }} animate={{ y: 0 }} exit={{ y: '100%' }} transition={{ type: 'spring', damping: 25 }} onClick={e => e.stopPropagation()} style={{ width: '100%', maxWidth: 480, height: '85vh', backgroundColor: '#FFFFFF', borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: 24, display: 'flex', flexDirection: 'column' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <h3 style={{ fontSize: '1.15rem', fontWeight: 900, color: '#191F28', margin: 0 }}>고객센터 &amp; 자주 묻는 질문</h3>
                <button onClick={() => setActiveModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}><X size={20} /></button>
              </div>

              <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 16 }}>
                {/* FAQ Accordion List */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{ fontSize: '0.82rem', fontWeight: 850, color: '#2FA86A' }}>자주 묻는 질문 (FAQ)</div>
                  {faqs.map((faq, idx) => (
                    <div key={idx} style={{ backgroundColor: '#F8FAF8', borderRadius: 16, border: '1px solid #EAEFEA', overflow: 'hidden' }}>
                      <div
                        onClick={() => setExpandedFaq(expandedFaq === idx ? null : idx)}
                        style={{ padding: '14px 16px', fontSize: '0.88rem', fontWeight: 800, color: '#191F28', display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer' }}
                      >
                        <span>Q. {faq.q}</span>
                        <ChevronRight size={16} style={{ transform: expandedFaq === idx ? 'rotate(90deg)' : 'none', transition: 'transform 0.2s' }} />
                      </div>
                      {expandedFaq === idx && (
                        <div style={{ padding: '0 16px 14px 16px', fontSize: '0.82rem', color: '#6E7671', lineHeight: 1.55, borderTop: '1px solid #F0F4F1', paddingTop: 10 }}>
                          {faq.a}
                        </div>
                      )}
                    </div>
                  ))}
                </div>

                {/* 1:1 Inquiry Form */}
                <form onSubmit={handleInquirySubmit} style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{ fontSize: '0.82rem', fontWeight: 850, color: '#2FA86A' }}>1:1 문의 남기기</div>
                  <textarea
                    placeholder="서비스 이용 중 궁금하신 점이나 제안 사항을 남겨주시면 담당자가 신속히 답변해 드립니다."
                    value={inquiryText}
                    onChange={e => setInquiryText(e.target.value)}
                    rows={4}
                    style={{ width: '100%', borderRadius: 16, backgroundColor: '#F8FAF8', border: '1px solid #E1E8E4', padding: 14, fontSize: '0.86rem', outline: 'none', resize: 'none', boxSizing: 'border-box' }}
                  />
                  {inquirySuccess && (
                    <div style={{ backgroundColor: '#E9F7EC', color: '#2FA86A', borderRadius: 12, padding: '10px 14px', fontSize: '0.82rem', fontWeight: 800, textAlign: 'center' }}>
                      ✓ 문의사항이 성공적으로 접수되었습니다!
                    </div>
                  )}
                  <button type="submit" disabled={!inquiryText.trim() || isSubmittingInquiry} className="btn-farm-primary" style={{ width: '100%', height: 46, borderRadius: 14, fontSize: '0.9rem', gap: 6 }}>
                    <Send size={16} /> {isSubmittingInquiry ? '접수 중...' : '문의 접수하기'}
                  </button>
                </form>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Community Post Detail Modal if user opens saved/my post */}
      <CommunityPostDetailModal
        post={selectedPost}
        onClose={() => setSelectedPost(null)}
        onToggleLike={onToggleLike}
        onToggleSave={onToggleSave}
        onAddComment={onAddComment}
      />

      {/* Floating AI Button */}
      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <img src="/svg-assets/ui-icons/ai-chat.svg" alt="AI 채팅" style={{ width: 26, height: 26, filter: 'brightness(0) invert(1)' }} />
      </button>

      {/* 4-Tab Bottom Navigation */}
      <BottomNavigation
        activeTab={activeTab}
        onTabChange={onTabChange}
        onOpenAIChat={onOpenAIChat}
      />
    </div>
  );
};
