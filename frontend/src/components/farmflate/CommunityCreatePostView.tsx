import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Paperclip, Link as LinkIcon, Image as ImageIcon, X } from 'lucide-react';

interface CommunityCreatePostViewProps {
  onCancel: () => void;
  onSubmitPost: (title: string, content: string) => void;
}

export const CommunityCreatePostView: React.FC<CommunityCreatePostViewProps> = ({
  onCancel,
  onSubmitPost
}) => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [attachedFile, setAttachedFile] = useState<{ name: string; size: string } | null>({
    name: '2873278_IMG',
    size: '3.1MB'
  });

  const handleSubmit = () => {
    if (!title.trim() || !content.trim()) return;
    onSubmitPost(title, content);
  };

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', padding: '0 20px 40px 20px', justifyContent: 'space-between' }}>
      <div>
        {/* Header */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          height: 64,
          borderBottom: '1px solid #ECEFED',
          marginBottom: 20
        }}>
          <button
            onClick={onCancel}
            style={{ background: 'none', border: 'none', fontSize: '0.92rem', color: '#6F7772', fontWeight: 600, cursor: 'pointer' }}
          >
            취소
          </button>

          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#191F28', margin: 0 }}>
            게시글 작성
          </h1>

          <motion.button
            whileTap={{ scale: 0.95 }}
            onClick={handleSubmit}
            style={{
              padding: '8px 18px',
              borderRadius: 20,
              background: 'linear-gradient(135deg, #2FA86A, #258A55)',
              color: '#FFFFFF',
              fontSize: '0.88rem',
              fontWeight: 850,
              border: 'none',
              cursor: 'pointer',
              boxShadow: '0 4px 12px rgba(47, 168, 106, 0.25)'
            }}
          >
            게시
          </motion.button>
        </div>

        {/* Title Input */}
        <div style={{ marginBottom: 16 }}>
          <input
            type="text"
            placeholder="게시글 제목을 입력하세요"
            value={title}
            onChange={e => setTitle(e.target.value)}
            style={{
              width: '100%',
              height: 52,
              borderRadius: 16,
              border: '1px solid #E1E8E4',
              backgroundColor: '#FFFFFF',
              padding: '0 16px',
              fontSize: '0.96rem',
              fontWeight: 700,
              color: '#191F28',
              outline: 'none',
              boxSizing: 'border-box'
            }}
          />
        </div>

        {/* Content Textarea with Pure Pretendard Typography */}
        <div style={{
          borderRadius: 20,
          border: '1px solid #E1E8E4',
          backgroundColor: '#FFFFFF',
          padding: '16px',
          marginBottom: 16,
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.02)'
        }}>
          <textarea
            placeholder="농사 경험, 밭 상태 질문 등 이웃 농부들과 공유할 내용을 자유롭게 적어보세요."
            value={content}
            onChange={e => setContent(e.target.value)}
            rows={8}
            style={{
              width: '100%',
              border: 'none',
              outline: 'none',
              resize: 'none',
              fontSize: '0.92rem',
              fontWeight: 500,
              fontFamily: "'Pretendard', sans-serif",
              lineHeight: 1.65,
              color: '#191F28',
              backgroundColor: 'transparent',
              boxSizing: 'border-box'
            }}
          />

          {/* Bottom Toolbar inside Textarea Container */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, paddingTop: 12, borderTop: '1px solid #F1F5F9', color: '#6F7772', fontSize: '0.82rem', fontWeight: 600 }}>
            <button style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 4, color: '#6F7772', cursor: 'pointer', padding: 0 }}>
              <Paperclip size={16} /> 파일
            </button>
            <button style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 4, color: '#6F7772', cursor: 'pointer', padding: 0 }}>
              <LinkIcon size={16} /> 링크
            </button>
            <button style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 4, color: '#6F7772', cursor: 'pointer', padding: 0 }}>
              <ImageIcon size={16} /> 이미지
            </button>
          </div>
        </div>

        {/* Attached File Preview Pill */}
        {attachedFile && (
          <div style={{
            backgroundColor: '#F8FAF8',
            borderRadius: 16,
            border: '1px solid #E1E8E4',
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <div style={{ width: 36, height: 36, borderRadius: 10, backgroundColor: '#E9F7EC', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <ImageIcon size={20} color="#2FA86A" />
              </div>
              <span style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28' }}>
                {attachedFile.name}
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <span style={{ fontSize: '0.76rem', color: '#9CA3AF', fontWeight: 600 }}>{attachedFile.size}</span>
              <button onClick={() => setAttachedFile(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 2 }}>
                <X size={16} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
