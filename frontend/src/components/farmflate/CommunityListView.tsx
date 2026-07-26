import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Plus, MessageSquare as MsgIcon, Heart, Bot } from 'lucide-react';
import type { CommunityPost, TabState } from '../../types/farmflate';
import { BottomNavigation } from '../common/BottomNavigation';
import { CommunityPostDetailModal } from './CommunityPostDetailModal';

interface CommunityListViewProps {
  posts: CommunityPost[];
  loadError?: string | null;
  onOpenAIChat: () => void;
  onOpenWrite?: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
  onToggleLike: (postId: string) => void;
  onToggleSave: (postId: string) => void;
  onAddComment: (postId: string, commentText: string) => void;
}

export const CommunityListView: React.FC<CommunityListViewProps> = ({
  posts,
  loadError,
  onOpenAIChat,
  onOpenWrite,
  activeTab,
  onTabChange,
  onToggleLike,
  onToggleSave,
  onAddComment
}) => {
  const [selectedPost, setSelectedPost] = useState<CommunityPost | null>(null);

  // Active post detail reference
  const activeDetailPost = selectedPost
    ? posts.find(p => p.id === selectedPost.id) ?? null
    : null;

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view no-scrollbar" style={{ padding: '32px 20px 96px 20px', overflowY: 'auto' }}>

        {/* Top Header: '커뮤니티' + '+ 글쓰기' green pill button */}
        <div style={{ marginBottom: 20 }}>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 900, color: '#191F28', margin: 0, marginTop: -10, letterSpacing: '-0.03em' }}>
            커뮤니티
          </h2>
          <div style={{ height: 1, backgroundColor: '#E5E8EB', width: '100%', marginTop: 5 }} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10 }}>
            <motion.button
              whileTap={{ scale: 0.95 }}
              onClick={onOpenWrite}
              style={{
                backgroundColor: '#2FA86A', color: '#FFFFFF', border: 'none',
                borderRadius: 20, padding: '8px 16px', fontSize: '0.84rem',
                fontWeight: 900, cursor: 'pointer', display: 'flex',
                alignItems: 'center', gap: 4
              }}
            >
              <Plus size={16} /> 글쓰기
            </motion.button>
          </div>
        </div>

        {/* Post Cards List or Empty State */}
        {loadError && (
          <div role="alert" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '20px', marginBottom: 16, textAlign: 'center', borderRadius: 16, backgroundColor: '#FFF4F0' }}>
            <h4 style={{ fontSize: '1.05rem', fontWeight: 850, color: '#191F28', margin: '0 0 6px 0' }}>게시글을 불러오지 못했습니다.</h4>
            <p style={{ fontSize: '0.82rem', color: '#B54708', margin: 0 }}>{loadError}</p>
          </div>
        )}
        {posts.length === 0 ? (
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '0 20px', textAlign: 'center' }}>
            <h4 style={{ fontSize: '1.05rem', fontWeight: 840, color: '#191F28', margin: '0 0 6px 0' }}>아직 등록된 게시글이 없습니다.</h4>
            <p style={{ fontSize: '0.82rem', color: '#6E7671', margin: '0 0 50px 0' }}>첫 번째 농가 노하우나 궁금한 질문을 남겨보세요!</p>
            <button onClick={onOpenWrite} className="btn-farm-primary" style={{ height: 44, padding: '0 20px', borderRadius: 14, fontSize: '0.85rem' }}>
              + 첫 글 작성하기
            </button>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {posts.map((post) => {
              return (
                <motion.div
                  key={post.id}
                  whileTap={{ scale: 0.99 }}
                  onClick={() => setSelectedPost(post)}
                  style={{
                    backgroundColor: '#F8FAF8', borderRadius: 20,
                    padding: '18px 20px', border: '1px solid #EAEFEA',
                    cursor: 'pointer', transition: 'all 0.2s ease'
                  }}
                >
                  {/* Tag Pill + Time Ago */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                    <span className="region-badge" style={{
                      backgroundColor: '#E9F7EC', color: '#2FA86A',
                      fontSize: '0.76rem', fontWeight: 800,
                      padding: '4px 12px', borderRadius: 12
                    }}>
                      {post.regionLabel || '지역 정보 없음'}
                    </span>
                    <span style={{ fontSize: '0.76rem', color: '#8E9892', fontWeight: 500 }}>
                      {post.timeAgo}
                    </span>
                  </div>

                  {/* Title */}
                  <h3 style={{
                    fontSize: '1.05rem', fontWeight: 900, color: '#191F28',
                    marginBottom: 6, lineHeight: 1.4, wordBreak: 'keep-all', letterSpacing: '-0.02em'
                  }}>
                    {post.title}
                  </h3>

                  {/* Content Preview */}
                  <p style={{
                    fontSize: '0.84rem', color: '#6E7671', lineHeight: 1.55,
                    fontWeight: 500, marginBottom: 14, wordBreak: 'keep-all',
                    display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical',
                    overflow: 'hidden'
                  }}>
                    {post.content}
                  </p>

                  {/* Stats Row: Msg icon + count, Heart icon + count */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 16, fontSize: '0.78rem', color: '#8E9892', fontWeight: 700 }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      <MsgIcon size={15} color="#8E9892" /> {post.comments?.length || post.commentCount}
                    </span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, color: post.isLiked ? '#FF4D4F' : '#8E9892' }}>
                      <Heart size={15} fill={post.isLiked ? '#FF4D4F' : 'none'} color={post.isLiked ? '#FF4D4F' : '#8E9892'} /> {post.likeCount}
                    </span>
                  </div>
                </motion.div>
              );
            })}
          </div>
        )}
      </div>

      {/* Post Detail Modal */}
      <CommunityPostDetailModal
        post={activeDetailPost}
        onClose={() => setSelectedPost(null)}
        onToggleLike={onToggleLike}
        onToggleSave={onToggleSave}
        onAddComment={onAddComment}
      />

      {/* Floating AI Button matching all other screens 100% */}
      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <Bot size={26} color="#FFFFFF" />
      </button>

      {/* Bottom Navigation */}
      <BottomNavigation
        activeTab={activeTab}
        onTabChange={onTabChange}
        onOpenAIChat={onOpenAIChat}
      />
    </div>
  );
};
