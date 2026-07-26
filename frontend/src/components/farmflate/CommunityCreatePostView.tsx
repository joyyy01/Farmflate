import React, { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Folder, Link as LinkIcon, Image as ImageIcon, X } from 'lucide-react';
import { ApiService, ApiError } from '../../services/api';

interface CommunityCreatePostViewProps {
  onCancel: () => void;
  onSubmitPost: (title: string, content: string, attachmentIds?: string[]) => void | Promise<void>;
  errorMessage?: string | null;
}

type DraftAttachment = {
  localId: string;
  type: 'IMAGE' | 'FILE' | 'LINK';
  name: string;
  previewUrl?: string;
  uploading: boolean;
  uploadedId?: string;
  error?: string;
};

const MAX_ATTACHMENTS = 5;

export const CommunityCreatePostView: React.FC<CommunityCreatePostViewProps> = ({
  onCancel,
  onSubmitPost,
  errorMessage
}) => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [attachments, setAttachments] = useState<DraftAttachment[]>([]);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const imageInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    return () => {
      attachments.forEach(item => { if (item.previewUrl) URL.revokeObjectURL(item.previewUrl); });
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const anyUploading = attachments.some(a => a.uploading);
  const anyFailed = attachments.some(a => a.error);

  const uploadImage = async (file: File) => {
    if (attachments.length >= MAX_ATTACHMENTS) return;
    const localId = crypto.randomUUID();
    const previewUrl = URL.createObjectURL(file);
    setAttachments(prev => [...prev, { localId, type: 'IMAGE', name: file.name, previewUrl, uploading: true }]);
    try {
      const result = await ApiService.uploadCommunityImage(file);
      setAttachments(prev => prev.map(a => a.localId === localId ? { ...a, uploading: false, uploadedId: result.id } : a));
    } catch (err) {
      const message = err instanceof ApiError ? err.message : '이미지 업로드에 실패했습니다.';
      setAttachments(prev => prev.map(a => a.localId === localId ? { ...a, uploading: false, error: message } : a));
    }
  };

  const uploadFile = async (file: File) => {
    if (attachments.length >= MAX_ATTACHMENTS) return;
    const localId = crypto.randomUUID();
    setAttachments(prev => [...prev, { localId, type: 'FILE', name: file.name, uploading: true }]);
    try {
      const result = await ApiService.uploadCommunityFile(file);
      setAttachments(prev => prev.map(a => a.localId === localId ? { ...a, uploading: false, uploadedId: result.id } : a));
    } catch (err) {
      const message = err instanceof ApiError ? err.message : '파일 업로드에 실패했습니다.';
      setAttachments(prev => prev.map(a => a.localId === localId ? { ...a, uploading: false, error: message } : a));
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (file) void uploadFile(file);
  };

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (file) void uploadImage(file);
  };

  const handleAddLink = async () => {
    if (attachments.length >= MAX_ATTACHMENTS) return;
    const inputUrl = window.prompt('첨부할 링크(URL)를 입력해 주세요 (https:// 로 시작해야 해요):');
    if (!inputUrl || !inputUrl.trim()) return;
    const cleanUrl = inputUrl.trim();
    const localId = crypto.randomUUID();
    setAttachments(prev => [...prev, { localId, type: 'LINK', name: cleanUrl, uploading: true }]);
    try {
      const result = await ApiService.createCommunityLink(cleanUrl);
      setAttachments(prev => prev.map(a => a.localId === localId ? { ...a, uploading: false, uploadedId: result.id } : a));
    } catch (err) {
      const message = err instanceof ApiError ? err.message : '링크를 추가하지 못했습니다.';
      setAttachments(prev => prev.map(a => a.localId === localId ? { ...a, uploading: false, error: message } : a));
    }
  };

  const removeAttachment = (localId: string) => {
    setAttachments(prev => {
      const target = prev.find(a => a.localId === localId);
      if (target?.previewUrl) URL.revokeObjectURL(target.previewUrl);
      return prev.filter(a => a.localId !== localId);
    });
  };

  const canSubmit = title.trim() && content.trim() && !isSubmitting && !anyUploading && !anyFailed;

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setIsSubmitting(true);
    try {
      const attachmentIds = attachments.map(a => a.uploadedId).filter((id): id is string => Boolean(id));
      await onSubmitPost(title.trim(), content.trim(), attachmentIds);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="full-screen-view" style={{ backgroundColor: '#FFFFFF', padding: '24px 20px 40px 20px', justifyContent: 'space-between', flex: 1, overflowY: 'auto' }}>
      <div>
        <div style={{
          display: 'grid', gridTemplateColumns: '72px 1fr 72px', alignItems: 'center',
          height: 64, borderBottom: '1px solid #ECEFED', marginBottom: 24
        }}>
          <button onClick={onCancel} style={{ justifySelf: 'start', background: 'none', border: 'none', fontSize: '0.94rem', color: '#6F7772', fontWeight: 600, cursor: 'pointer', padding: 0 }}>
            취소
          </button>
          <h1 style={{ fontSize: '1.1rem', fontWeight: 850, color: '#191F28', margin: 0, textAlign: 'center', whiteSpace: 'nowrap' }}>
            게시글 작성
          </h1>
          <div style={{ justifySelf: 'end' }}>
            <motion.button
              whileTap={{ scale: 0.95 }}
              onClick={handleSubmit}
              disabled={!canSubmit}
              style={{
                height: 36, padding: '0 18px', borderRadius: 18,
                backgroundColor: canSubmit ? '#2FA86A' : '#C8D5CE',
                color: '#FFFFFF', fontSize: '0.88rem', fontWeight: 850, border: 'none',
                cursor: canSubmit ? 'pointer' : 'default',
                whiteSpace: 'nowrap', display: 'inline-flex', alignItems: 'center', justifyContent: 'center'
              }}
            >
              {isSubmitting ? '등록 중...' : '게시'}
            </motion.button>
          </div>
        </div>
        {errorMessage && (
          <p role="alert" style={{ margin: '0 0 16px', borderRadius: 12, padding: '10px 14px', backgroundColor: '#FFF4F0', color: '#B54708', fontSize: '0.82rem', fontWeight: 700 }}>
            {errorMessage}
          </p>
        )}

        <div style={{ marginBottom: 16 }}>
          <input
            type="text"
            placeholder="게시글 제목을 입력하세요"
            value={title}
            onChange={e => setTitle(e.target.value)}
            style={{ width: '100%', height: 54, borderRadius: 16, border: '1px solid #E1E8E4', backgroundColor: '#FFFFFF', padding: '0 16px', fontSize: '0.96rem', fontWeight: 600, color: '#191F28', outline: 'none', boxSizing: 'border-box' }}
          />
        </div>

        <div style={{ borderRadius: 20, border: '1px solid #E1E8E4', backgroundColor: '#FFFFFF', padding: '18px 16px 14px 16px', marginBottom: 18 }}>
          <textarea
            placeholder="내용을 입력하세요..."
            value={content}
            onChange={e => setContent(e.target.value)}
            rows={10}
            style={{ width: '100%', border: 'none', outline: 'none', resize: 'none', fontSize: '0.94rem', fontWeight: 500, fontFamily: "'Pretendard', sans-serif", lineHeight: 1.65, color: '#191F28', backgroundColor: 'transparent', boxSizing: 'border-box' }}
          />

          <input type="file" ref={fileInputRef} onChange={handleFileUpload} style={{ display: 'none' }} />
          <input type="file" accept="image/jpeg,image/png,image/webp" ref={imageInputRef} onChange={handleImageUpload} style={{ display: 'none' }} />

          <div style={{ display: 'flex', alignItems: 'center', gap: 18, paddingTop: 14, borderTop: '1px solid #F1F5F9', color: '#6F7772', fontSize: '0.84rem', fontWeight: 600 }}>
            <button type="button" disabled={attachments.length >= MAX_ATTACHMENTS} onClick={() => fileInputRef.current?.click()} style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 6, color: '#6F7772', cursor: 'pointer', padding: 0 }}>
              <Folder size={16} /> 파일
            </button>
            <button type="button" disabled={attachments.length >= MAX_ATTACHMENTS} onClick={handleAddLink} style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 6, color: '#6F7772', cursor: 'pointer', padding: 0 }}>
              <LinkIcon size={16} /> 링크
            </button>
            <button type="button" disabled={attachments.length >= MAX_ATTACHMENTS} onClick={() => imageInputRef.current?.click()} style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center', gap: 6, color: '#6F7772', cursor: 'pointer', padding: 0 }}>
              <ImageIcon size={16} /> 이미지
            </button>
            <span style={{ marginLeft: 'auto', fontSize: '0.74rem', color: '#9CA3AF' }}>{attachments.length}/{MAX_ATTACHMENTS}</span>
          </div>
        </div>

        {attachments.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {attachments.map(item => (
              <div key={item.localId} style={{ backgroundColor: '#F8FAF8', borderRadius: 16, border: '1px solid #EAEFEA', padding: '12px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0, flex: 1 }}>
                  <div style={{ width: 36, height: 36, borderRadius: 10, flexShrink: 0, backgroundColor: '#FFFFFF', border: '1px solid #EAEFEA', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden' }}>
                    {item.type === 'IMAGE' && item.previewUrl ? (
                      <img src={item.previewUrl} alt="첨부 이미지 미리보기" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    ) : item.type === 'LINK' ? (
                      <LinkIcon size={18} color="#0284C7" />
                    ) : (
                      <Folder size={18} color="#8E9892" />
                    )}
                  </div>
                  <span style={{ fontSize: '0.88rem', fontWeight: 800, color: '#191F28', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {item.name}
                  </span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}>
                  {item.uploading && <span style={{ fontSize: '0.74rem', color: '#8E9892' }}>업로드 중...</span>}
                  {item.error && <span style={{ fontSize: '0.72rem', color: '#DC2626' }}>{item.error}</span>}
                  <button type="button" onClick={() => removeAttachment(item.localId)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 2, display: 'flex' }}>
                    <X size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
