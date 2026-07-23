import React from 'react';
import { Plus, MessageSquare as MsgIcon, Heart } from 'lucide-react';
import type { CommunityPost, TabState } from '../../types/farmflate';
import { BottomNavigation } from '../common/BottomNavigation';

interface CommunityListViewProps {
  posts: CommunityPost[];
  onOpenAIChat: () => void;
  onOpenWrite?: () => void;
  activeTab: TabState;
  onTabChange: (tab: TabState) => void;
}

export const CommunityListView: React.FC<CommunityListViewProps> = ({
  posts,
  onOpenAIChat,
  onOpenWrite,
  activeTab,
  onTabChange
}) => {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', position: 'relative' }}>
      <div className="full-screen-view" style={{ padding: '20px 20px 96px 20px' }}>

        {/* Header: '커뮤니티' + '+ 글쓰기' pill button */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 900, color: '#191F28', margin: 0 }}>
            커뮤니티
          </h2>
          <button onClick={onOpenWrite} style={{
            backgroundColor: '#2E8B57', color: '#FFFFFF', border: 'none',
            borderRadius: 20, padding: '8px 14px', fontSize: '0.82rem',
            fontWeight: 800, cursor: 'pointer', display: 'flex',
            alignItems: 'center', gap: 4
          }}>
            <Plus size={14} /> 글쓰기
          </button>
        </div>

        {/* Post Cards - Design File 3 Screen 3 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {posts.map(post => (
            <div key={post.id} style={{
              backgroundColor: '#FFFFFF', borderRadius: 18,
              padding: '16px 18px', border: '1px solid #E5E8EB',
              cursor: 'pointer'
            }}>
              {/* Tag + Time */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                <span style={{
                  backgroundColor: '#EDF7ED', color: '#2E8B57',
                  fontSize: '0.74rem', fontWeight: 800,
                  padding: '4px 10px', borderRadius: 8
                }}>
                  {post.tagLocation || post.category}
                </span>
                <span style={{ fontSize: '0.74rem', color: '#B0B8C1', fontWeight: 500 }}>
                  {post.timeAgo}
                </span>
              </div>

              {/* Title */}
              <h4 style={{ fontSize: '0.96rem', fontWeight: 900, color: '#191F28', marginBottom: 6, lineHeight: 1.4 }}>
                {post.title}
              </h4>
              {/* Content preview */}
              <p style={{ fontSize: '0.82rem', color: '#6B7280', lineHeight: 1.5, fontWeight: 500, marginBottom: 12 }}>
                {post.content}
              </p>

              {/* Comment + Like counts */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 14, fontSize: '0.78rem', color: '#8B95A1', fontWeight: 600 }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <MsgIcon size={14} /> {post.commentCount}
                </span>
                <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <Heart size={14} /> {post.likeCount}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Floating AI Button */}
      <button className="floating-ai-btn" onClick={onOpenAIChat} title="AI Assistant">
        <img src="/svg-assets/ui-icons/ai-chat.svg" alt="AI 채팅" style={{ width: 26, height: 26, filter: 'brightness(0) invert(1)' }} />
      </button>

      {/* Exact Figma Vector SVG Bottom Navigation 1:1 */}
      <BottomNavigation
        activeTab={activeTab}
        onTabChange={onTabChange}
        onOpenAIChat={onOpenAIChat}
      />
    </div>
  );
};
