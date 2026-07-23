import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Heart, Bookmark, MessageSquare, Send, MapPin, UserCheck } from 'lucide-react';
import type { CommunityPost } from '../../types/farmflate';

interface CommunityPostDetailModalProps {
  post: CommunityPost | null;
  onClose: () => void;
  onToggleLike: (postId: string) => void;
  onToggleSave: (postId: string) => void;
  onAddComment: (postId: string, commentText: string) => void;
}

export const CommunityPostDetailModal: React.FC<CommunityPostDetailModalProps> = ({
  post,
  onClose,
  onToggleLike,
  onToggleSave,
  onAddComment
}) => {
  const [commentInput, setCommentInput] = useState('');

  if (!post) return null;

  const handleCommentSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentInput.trim()) return;
    onAddComment(post.id, commentInput.trim());
    setCommentInput('');
  };

  return (
    <AnimatePresence>
      <div style={{
        position: 'fixed', inset: 0, zIndex: 1000,
        backgroundColor: 'rgba(0, 0, 0, 0.45)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'flex-end', justifyContent: 'center'
      }} onClick={onClose}>
        
        <motion.div
          initial={{ y: '100%' }}
          animate={{ y: 0 }}
          exit={{ y: '100%' }}
          transition={{ type: 'spring', damping: 25, stiffness: 300 }}
          onClick={(e) => e.stopPropagation()}
          style={{
            width: '100%', maxWidth: 480, height: '90vh',
            backgroundColor: '#FFFFFF', borderTopLeftRadius: 28, borderTopRightRadius: 28,
            display: 'flex', flexDirection: 'column', overflow: 'hidden',
            boxShadow: '0 -10px 40px rgba(0, 0, 0, 0.15)'
          }}
        >
          {/* Header Bar */}
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '18px 20px', borderBottom: '1px solid #F0F2F1'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{
                backgroundColor: '#E9F7EC', color: '#2FA86A',
                fontSize: '0.78rem', fontWeight: 850,
                padding: '4px 12px', borderRadius: 12
              }}>
                {post.category}
              </span>
              {post.tagLocation && (
                <span style={{ fontSize: '0.78rem', color: '#6E7671', fontWeight: 600, display: 'flex', alignItems: 'center', gap: 3 }}>
                  <MapPin size={12} color="#2FA86A" /> {post.tagLocation}
                </span>
              )}
            </div>

            <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 4, display: 'flex' }}>
              <X size={22} color="#191F28" />
            </button>
          </div>

          {/* Scrollable Main Content */}
          <div className="no-scrollbar" style={{ flex: 1, overflowY: 'auto', padding: '20px' }}>
            
            {/* Author Profile Bar */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{
                  width: 40, height: 40, borderRadius: '50%', backgroundColor: '#F0FAF3',
                  border: '1.5px solid #2FA86A', display: 'flex', alignItems: 'center', justifyContent: 'center'
                }}>
                  <UserCheck size={20} color="#2FA86A" />
                </div>
                <div>
                  <div style={{ fontSize: '0.94rem', fontWeight: 850, color: '#191F28' }}>
                    {post.author}
                  </div>
                  <div style={{ fontSize: '0.74rem', color: '#8E9892', fontWeight: 500 }}>
                    {post.timeAgo}
                  </div>
                </div>
              </div>

              {/* Bookmark Button */}
              <button
                onClick={() => onToggleSave(post.id)}
                style={{
                  background: post.isSaved ? '#E0F2FE' : '#F8FAF8',
                  border: post.isSaved ? '1px solid #0284C7' : '1px solid #EAEFEA',
                  borderRadius: 12, padding: '6px 12px',
                  fontSize: '0.78rem', fontWeight: 800,
                  color: post.isSaved ? '#0284C7' : '#6E7671',
                  cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4
                }}
              >
                <Bookmark size={14} fill={post.isSaved ? '#0284C7' : 'none'} color={post.isSaved ? '#0284C7' : '#6E7671'} />
                {post.isSaved ? '저장됨' : '저장'}
              </button>
            </div>

            {/* Post Title */}
            <h2 style={{
              fontSize: '1.25rem', fontWeight: 900, color: '#191F28',
              lineHeight: 1.4, marginBottom: 12, wordBreak: 'keep-all', wordWrap: 'break-word'
            }}>
              {post.title}
            </h2>

            {/* Attached Image Preview */}
            {post.imageUrl && (
              <div style={{ marginBottom: 16, borderRadius: 16, overflow: 'hidden', border: '1px solid #EAEFEA' }}>
                <img src={post.imageUrl} alt="첨부 이미지" style={{ width: '100%', maxHeight: 260, objectFit: 'cover', display: 'block' }} />
              </div>
            )}

            {/* Post Content */}
            <p style={{
              fontSize: '0.94rem', color: '#333D4B', fontWeight: 500,
              lineHeight: 1.65, whiteSpace: 'pre-line', margin: '0 0 24px 0',
              wordBreak: 'keep-all', wordWrap: 'break-word'
            }}>
              {post.content}
            </p>

            {/* Like & Interaction Bar */}
            <div style={{
              display: 'flex', alignItems: 'center', gap: 16, padding: '12px 16px',
              backgroundColor: '#F8FAF8', borderRadius: 14, border: '1px solid #EAEFEA',
              marginBottom: 24
            }}>
              <button
                onClick={() => onToggleLike(post.id)}
                style={{
                  background: 'none', border: 'none', cursor: 'pointer', padding: 0,
                  display: 'flex', alignItems: 'center', gap: 6,
                  color: post.isLiked ? '#FF4D4F' : '#6E7671',
                  fontSize: '0.86rem', fontWeight: 800
                }}
              >
                <Heart size={18} fill={post.isLiked ? '#FF4D4F' : 'none'} color={post.isLiked ? '#FF4D4F' : '#6E7671'} />
                좋아요 {post.likeCount}
              </button>

              <div style={{ width: 1, height: 16, backgroundColor: '#D1DFD7' }} />

              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.86rem', fontWeight: 800, color: '#6E7671' }}>
                <MessageSquare size={18} color="#2FA86A" />
                댓글 {post.comments?.length || post.commentCount}
              </div>
            </div>

            {/* Comments Section Header */}
            <div style={{ marginBottom: 14 }}>
              <h3 style={{ fontSize: '1.05rem', fontWeight: 850, color: '#191F28', margin: 0 }}>
                댓글 ({post.comments?.length || 0})
              </h3>
            </div>

            {/* Comment List */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 20 }}>
              {(!post.comments || post.comments.length === 0) ? (
                <div style={{
                  padding: '24px 0', textAlign: 'center', color: '#8E9892',
                  fontSize: '0.84rem', fontWeight: 600
                }}>
                  아직 작성된 댓글이 없습니다. 첫 댓글을 남겨보세요!
                </div>
              ) : (
                post.comments.map(c => (
                  <div key={c.id} style={{
                    backgroundColor: '#F8FAF8', borderRadius: 14, padding: '12px 14px',
                    border: '1px solid #EAEFEA'
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                      <span style={{ fontSize: '0.82rem', fontWeight: 850, color: '#191F28' }}>
                        {c.author}
                      </span>
                      <span style={{ fontSize: '0.72rem', color: '#8E9892', fontWeight: 500 }}>
                        {c.timeAgo}
                      </span>
                    </div>
                    <p style={{
                      fontSize: '0.86rem', color: '#333D4B', fontWeight: 500,
                      lineHeight: 1.5, margin: 0, wordBreak: 'keep-all'
                    }}>
                      {c.content}
                    </p>
                  </div>
                ))
              )}
            </div>

          </div>

          {/* Sticky Bottom Comment Input Bar */}
          <form onSubmit={handleCommentSubmit} style={{
            padding: '12px 16px 24px 16px', backgroundColor: '#FFFFFF',
            borderTop: '1px solid #F0F2F1', display: 'flex', gap: 10, alignItems: 'center'
          }}>
            <input
              type="text"
              placeholder="따뜻한 댓글을 남겨주세요..."
              value={commentInput}
              onChange={(e) => setCommentInput(e.target.value)}
              style={{
                flex: 1, height: 46, borderRadius: 14, backgroundColor: '#F8FAF8',
                border: '1.5px solid #D1DFD7', padding: '0 16px', fontSize: '0.88rem',
                fontWeight: 600, color: '#191F28', outline: 'none'
              }}
            />
            <motion.button
              whileTap={{ scale: 0.96 }}
              type="submit"
              disabled={!commentInput.trim()}
              style={{
                width: 46, height: 46, borderRadius: 14,
                backgroundColor: commentInput.trim() ? '#2FA86A' : '#E0E5E2',
                color: '#FFFFFF', border: 'none', cursor: commentInput.trim() ? 'pointer' : 'default',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                boxShadow: commentInput.trim() ? '0 4px 12px rgba(47, 168, 106, 0.25)' : 'none'
              }}
            >
              <Send size={18} />
            </motion.button>
          </form>

        </motion.div>
      </div>
    </AnimatePresence>
  );
};
