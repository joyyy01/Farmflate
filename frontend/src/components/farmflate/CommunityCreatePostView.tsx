import React, { useState, useRef } from 'react';
import { motion } from 'framer-motion';
import { Folder, Link as LinkIcon, Image as ImageIcon, X } from 'lucide-react';

interface CommunityCreatePostViewProps {
  onCancel: () => void;
  onSubmitPost: (title: string, content: string, category?: string, locationTag?: string, imageUrl?: string) => void | Promise<void>;
  userRegion?: string;
}

export const CommunityCreatePostView: React.FC<CommunityCreatePostViewProps> = ({
  onCancel,
  onSubmitPost,
  userRegion = '전북 고창군'
}) => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [attachedFile, setAttachedFile] = useState<{ name: string; size: string; url?: string; type?: 'file' | 'link' | 'image' } | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setAttachedFile({
          name: file.name,
          size: `${(file.size / (1024 * 1024)).toFixed(1)}MB`,
          url: reader.result as string,
          type: 'file'
        });
      };
      reader.readAsDataURL(file);
    }
  };

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setAttachedFile({
          name: file.name,
          size: `${(file.size / (1024 * 1024)).toFixed(1)}MB`,
          url: reader.result as string,
          type: 'image'
        });
      };
      reader.readAsDataURL(file);
    }
  };

  const handleAddLink = () => {
    const inputUrl = window.prompt('첨부할 링크(URL)를 입력해 주세요 (예: https://example.com):');
    if (inputUrl && inputUrl.trim()) {
      const cleanUrl = inputUrl.trim().startsWith('http') ? inputUrl.trim() : `https://${inputUrl.trim()}`;
      setAttachedFile({
        name: cleanUrl,
        size: '웹 링크',
        url: cleanUrl,
        type: 'link'
      });
    }
  };

  const handleSubmit = async () => {
    if (!title.trim() || !content.trim() || isSubmitting) return;
    setIsSubmitting(true);
    try {
      await onSubmitPost(
        title.trim(),
        content.trim(),
        '농가 노하우',
        userRegion,
        attachedFile?.url
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', padding: '24px 20px 40px 20px', justifyContent: 'space-between', flex: 1, overflowY: 'auto' }}>
      <div>
        {/* Header Grid - 100% Dead-Centered Title & No-Wrap Button */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '72px 1fr 72px',
          alignItems: 'center',
          height: 64,
          borderBottom: '1px solid #ECEFED',
          marginBottom: 24
        }}>
          <button
            onClick={onCancel}
            style={{ justifySelf: 'start', background: 'none', border: 'none', fontSize: '0.94rem', color: '#6F7772', fontWeight: 600, cursor: 'pointer', padding: 0 }}
          >
            취소
          </button>

          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center', whiteSpace: 'nowrap' }}>
            게시글 작성
          </h1>

          <div style={{ justifySelf: 'end' }}>
            <motion.button
              whileTap={{ scale: 0.95 }}
              onClick={handleSubmit}
              disabled={!title.trim() || !content.trim() || isSubmitting}
              style={{
                height: 36,
                padding: '0 18px',
                borderRadius: 18,
                backgroundColor: (!title.trim() || !content.trim() || isSubmitting) ? '#C8D5CE' : '#2FA86A',
                color: '#FFFFFF',
                fontSize: '0.88rem',
                fontWeight: 850,
                border: 'none',
                cursor: (!title.trim() || !content.trim() || isSubmitting) ? 'default' : 'pointer',
                whiteSpace: 'nowrap',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}
            >
              {isSubmitting ? '등록 중...' : '게시'}
            </motion.button>
          </div>
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
              height: 54,
              borderRadius: 16,
              border: '1px solid #E1E8E4',
              backgroundColor: '#FFFFFF',
              padding: '0 16px',
              fontSize: '0.96rem',
              fontWeight: 600,
              color: '#191F28',
              outline: 'none',
              boxSizing: 'border-box'
            }}
          />
        </div>

        {/* Content Textarea Container */}
        <div style={{
          borderRadius: 20,
          border: '1px solid #E1E8E4',
          backgroundColor: '#FFFFFF',
          padding: '18px 16px 14px 16px',
          marginBottom: 18
        }}>
          <textarea
            placeholder="내용을 입력하세요..."
            value={content}
            onChange={e => setContent(e.target.value)}
            rows={10}
            style={{
              width: '100%',
              border: 'none',
              outline: 'none',
              resize: 'none',
              fontSize: '0.94rem',
              fontWeight: 500,
              fontFamily: "'Pretendard', sans-serif",
              lineHeight: 1.65,
              color: '#191F28',
              backgroundColor: 'transparent',
              boxSizing: 'border-box'
            }}
          />

          {/* Hidden File & Image Inputs */}
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileUpload}
            style={{ display: 'none' }}
          />
          <input
            type="file"
            accept="image/*"
            ref={imageInputRef}
            onChange={handleImageUpload}
            style={{ display: 'none' }}
          />

          {/* Toolbar inside Content Box */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 18,
            paddingTop: 14, borderTop: '1px solid #F1F5F9',
            color: '#6F7772', fontSize: '0.84rem', fontWeight: 600
          }}>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 6, color: '#6F7772', cursor: 'pointer', padding: 0 }}
            >
              <Folder size={16} /> 파일
            </button>

            <button
              type="button"
              onClick={handleAddLink}
              style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 6, color: '#6F7772', cursor: 'pointer', padding: 0 }}
            >
              <LinkIcon size={16} /> 링크
            </button>

            <button
              type="button"
              onClick={() => imageInputRef.current?.click()}
              style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 6, color: '#6F7772', cursor: 'pointer', padding: 0 }}
            >
              <ImageIcon size={16} /> 이미지
            </button>
          </div>
        </div>

        {/* Attached File / Link / Image Pill Container */}
        {attachedFile && (
          <div style={{
            backgroundColor: '#F8FAF8',
            borderRadius: 16,
            border: '1px solid #EAEFEA',
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0, flex: 1 }}>
              <div style={{
                width: 36, height: 36, borderRadius: 10, flexShrink: 0,
                backgroundColor: '#FFFFFF', border: '1px solid #EAEFEA',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                overflow: 'hidden'
              }}>
                {attachedFile.type === 'image' && attachedFile.url ? (
                  <img src={attachedFile.url} alt="첨부 이미지" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                ) : attachedFile.type === 'link' ? (
                  <LinkIcon size={18} color="#0284C7" />
                ) : (
                  <Folder size={18} color="#8E9892" />
                )}
              </div>
              <span style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {attachedFile.name}
              </span>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexShrink: 0 }}>
              <span style={{ fontSize: '0.78rem', color: '#8E9892', fontWeight: 600 }}>
                {attachedFile.size}
              </span>
              <button
                type="button"
                onClick={() => setAttachedFile(null)}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 2, display: 'flex' }}
              >
                <X size={16} />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
