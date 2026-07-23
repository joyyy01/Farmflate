import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import type { PanInfo } from 'framer-motion';
import { ArrowUp, ChevronRight, X, Sparkles } from 'lucide-react';
import { ApiService } from '../../services/api';
import type { Message } from '../../types/chat';

interface AIChatModalProps {
  isOpen: boolean;
  onClose: () => void;
  selectedRegion?: string;
  selectedCropInfo?: string;
}

export const AIChatModal: React.FC<AIChatModalProps> = ({
  isOpen,
  onClose
}) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [hasStartedChat, setHasStartedChat] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);

  const detailedQuestions = [
    { title: '이 상태가 왜 그런가요?', desc: '작물 상태나 문제 원인을 알려드려요.', img: '/svg-assets/ai/faq-icons/state-question.svg' },
    { title: '오늘 물을 줘야 하나요?', desc: '날씨와 토양 정보를 바탕으로 알려드려요.', img: '/svg-assets/ai/faq-icons/season-question.svg' },
    { title: '이 시기에는 무엇을 조심해야 하나요?', desc: '시기별 주의사항과 관리 팁을 알려드려요.', img: '/svg-assets/ai/faq-icons/crop-question.svg' },
    { title: '농사 용어가 어려워요', desc: '쉬운 설명으로 이해를 도와드려요.', img: '/svg-assets/ai/faq-icons/term-question.svg' }
  ];

  useEffect(() => {
    if (messages.length > 0) {
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  const handleSendPrompt = async (promptText: string) => {
    if (!promptText.trim() || loading) return;
    setHasStartedChat(true);
    const userMsg: Message = {
      id: Date.now().toString(),
      sender: 'user',
      content: promptText,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const response = await ApiService.sendChatMessage({ message: promptText });
      const aiMsg: Message = {
        id: (Date.now() + 1).toString(),
        sender: 'assistant',
        content: response.reply,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      };
      setMessages(prev => [...prev, aiMsg]);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setHasStartedChat(false);
    setMessages([]);
  };

  const handleDragEnd = (_: any, info: PanInfo) => {
    if (info.offset.y > 80 || info.velocity.y > 350) {
      onClose();
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            style={{
              position: 'fixed',
              top: 0, left: 0, right: 0, bottom: 0,
              backgroundColor: 'rgba(0, 0, 0, 0.45)',
              backdropFilter: 'blur(4px)',
              zIndex: 99
            }}
          />

          {/* Bottom Sheet Modal with perfect bounds */}
          <motion.div
            drag="y"
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0, bottom: 0.8 }}
            onDragEnd={handleDragEnd}
            initial={{ y: '100%' }}
            animate={{ y: 0 }}
            exit={{ y: '100%' }}
            transition={{ type: 'spring', damping: 25, stiffness: 280 }}
            style={{
              position: 'fixed',
              bottom: 0, left: 0, right: 0,
              width: '100%',
              maxWidth: 480,
              margin: '0 auto',
              height: '88vh',
              backgroundColor: '#FFFFFF',
              borderTopLeftRadius: 28,
              borderTopRightRadius: 28,
              boxShadow: '0 -12px 40px rgba(0, 0, 0, 0.22)',
              zIndex: 100,
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden'
            }}
          >
            {/* Top Handle Bar & Close Button */}
            <div style={{ flexShrink: 0, paddingBottom: 4, cursor: 'grab' }}>
              <div style={{ display: 'flex', justifyContent: 'center', padding: '12px 0 4px' }}>
                <div style={{ width: 44, height: 5, borderRadius: 3, backgroundColor: '#CBD5E1' }} />
              </div>
              <div style={{ padding: '4px 20px', display: 'flex', justifyContent: 'flex-end' }}>
                <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 4 }}>
                  <X size={22} />
                </button>
              </div>
            </div>

            {/* Modal Body */}
            {!hasStartedChat ? (
              <div style={{ flex: 1, overflowY: 'auto', padding: '0 20px 24px 20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', marginBottom: 16 }}>
                    <img
                      src="/svg-assets/brand/mascot/guide.svg"
                      alt="Farmflate AI 캐릭터"
                      style={{ width: 135, height: 110, objectFit: 'contain', marginBottom: 6 }}
                    />
                    <div style={{ color: '#177043', fontSize: '1.2rem', fontWeight: 900, marginBottom: 2 }}>
                      Farmflate AI
                    </div>
                    <h1 style={{ fontSize: '1.45rem', fontWeight: 900, color: '#191F28', margin: '0 0 4px 0', lineHeight: 1.2 }}>
                      무엇을 도와드릴까요?
                    </h1>
                    <p style={{ fontSize: '0.82rem', color: '#6f7772', margin: 0 }}>
                      농사 초보도 쉽게! 궁금한 점을 물어보세요.
                    </p>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 18 }}>
                    {detailedQuestions.map((q, idx) => (
                      <motion.button
                        key={idx}
                        whileTap={{ scale: 0.98 }}
                        onClick={() => handleSendPrompt(q.title)}
                        style={{
                          width: '100%',
                          minHeight: 76,
                          border: '1px solid #d6e9d9',
                          borderRadius: 14,
                          background: 'linear-gradient(135deg, #fbfffc, #f3faf5)',
                          display: 'grid',
                          gridTemplateColumns: '55px 1fr 25px',
                          alignItems: 'center',
                          padding: '0 14px',
                          cursor: 'pointer',
                          textAlign: 'left',
                          boxShadow: '0 2px 8px rgba(47, 120, 72, 0.025)'
                        }}
                      >
                        <img src={q.img} alt="" style={{ width: 42, height: 44, objectFit: 'contain' }} />
                        <div>
                          <strong style={{ display: 'block', fontSize: '0.9rem', fontWeight: 850, color: '#191F28' }}>{q.title}</strong>
                          <p style={{ margin: '4px 0 0 0', fontSize: '0.74rem', color: '#777f7a' }}>{q.desc}</p>
                        </div>
                        <ChevronRight size={18} color="#34443a" />
                      </motion.button>
                    ))}
                  </div>
                </div>

                {/* Input Bar */}
                <div style={{
                  border: '1.8px solid #2f9f5c',
                  borderRadius: 29,
                  boxShadow: '0 5px 15px rgba(42, 143, 83, 0.13)',
                  backgroundColor: '#FFFFFF',
                  display: 'flex',
                  alignItems: 'center',
                  padding: '4px 4px 4px 16px',
                  marginBottom: 10
                }}>
                  <input
                    type="text"
                    placeholder="궁금한 것을 입력하세요..."
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleSendPrompt(input)}
                    style={{
                      flex: 1,
                      border: 'none',
                      outline: 'none',
                      fontSize: '0.88rem',
                      color: '#29322d',
                      backgroundColor: 'transparent'
                    }}
                  />
                  <button
                    onClick={() => handleSendPrompt(input)}
                    style={{
                      width: 39,
                      height: 39,
                      borderRadius: '50%',
                      background: 'linear-gradient(145deg, #36a565, #2c9458)',
                      color: '#FFFFFF',
                      border: 'none',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      cursor: 'pointer'
                    }}
                  >
                    <ArrowUp size={20} strokeWidth={2.5} />
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
                <div style={{ flex: 1, overflowY: 'auto', padding: '12px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 2 }}>
                    <span style={{ fontSize: '0.82rem', color: '#2e9f5b', fontWeight: 800 }}>Farmflate AI</span>
                    <button onClick={handleReset} style={{ background: 'none', border: 'none', color: '#9CA3AF', fontSize: '0.76rem', cursor: 'pointer' }}>
                      처음으로
                    </button>
                  </div>

                  {messages.map(msg => (
                    <div
                      key={msg.id}
                      style={{
                        alignSelf: msg.sender === 'user' ? 'flex-end' : 'flex-start',
                        maxWidth: '85%',
                        padding: '12px 16px',
                        borderRadius: 18,
                        fontSize: '0.88rem',
                        lineHeight: 1.5,
                        backgroundColor: msg.sender === 'user' ? '#2e9f5b' : '#F8FAFC',
                        color: msg.sender === 'user' ? '#FFFFFF' : '#191F28',
                        border: msg.sender === 'assistant' ? '1px solid #E2E8F0' : 'none'
                      }}
                    >
                      <div>{msg.content}</div>
                      <div style={{ fontSize: '0.66rem', marginTop: 4, opacity: 0.7, textAlign: 'right' }}>
                        {msg.timestamp}
                      </div>
                    </div>
                  ))}

                  {loading && (
                    <div style={{ alignSelf: 'flex-start', padding: '12px 16px', backgroundColor: '#F8FAFC', borderRadius: 18, fontSize: '0.84rem', color: '#6B7280', display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Sparkles size={16} color="#2e9f5b" /> AI 답변을 생성하고 있어요...
                    </div>
                  )}
                  <div ref={chatEndRef} />
                </div>

                <div style={{ flexShrink: 0, padding: '12px 20px 24px 20px', backgroundColor: '#FFFFFF', borderTop: '1px solid #F1F5F9' }}>
                  <div style={{
                    border: '1.8px solid #2f9f5c',
                    borderRadius: 29,
                    boxShadow: '0 5px 15px rgba(42, 143, 83, 0.13)',
                    backgroundColor: '#FFFFFF',
                    display: 'flex',
                    alignItems: 'center',
                    padding: '4px 4px 4px 16px'
                  }}>
                    <input
                      type="text"
                      placeholder="궁금한 것을 입력하세요..."
                      value={input}
                      onChange={e => setInput(e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && handleSendPrompt(input)}
                      style={{
                        flex: 1,
                        border: 'none',
                        outline: 'none',
                        fontSize: '0.88rem',
                        color: '#29322d',
                        backgroundColor: 'transparent'
                      }}
                    />
                    <button
                      onClick={() => handleSendPrompt(input)}
                      style={{
                        width: 39,
                        height: 39,
                        borderRadius: '50%',
                        background: 'linear-gradient(145deg, #36a565, #2c9458)',
                        color: '#FFFFFF',
                        border: 'none',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer'
                      }}
                    >
                      <ArrowUp size={20} strokeWidth={2.5} />
                    </button>
                  </div>
                </div>
              </div>
            )}
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
};
