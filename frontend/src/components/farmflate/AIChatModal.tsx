import React, { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import type { PanInfo } from 'framer-motion';
import { ArrowUp, ChevronRight, X, Sparkles } from 'lucide-react';
import { ApiError, ApiService } from '../../services/api';
import type { Message, AIChatContext } from '../../types/chat';
import { getChatSuggestions } from '../../services/chatSuggestions';
import { visibleDataSignature } from '../../services/visibleDataContext';

interface AIChatModalProps {
  isOpen: boolean;
  onClose: () => void;
  context: AIChatContext;
}

export const AIChatModal: React.FC<AIChatModalProps> = ({
  isOpen,
  onClose,
  context
}) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [hasStartedChat, setHasStartedChat] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastFailedPrompt, setLastFailedPrompt] = useState<string | null>(null);
  const [lastFailedMessageId, setLastFailedMessageId] = useState<string | null>(null);
  const chatEndRef = useRef<HTMLDivElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const sessionIdRef = useRef(0);

  const detailedQuestions = getChatSuggestions(context);
  const visibleDataKey = visibleDataSignature(context.visibleData);

  const resetConversation = () => {
    abortControllerRef.current?.abort();
    sessionIdRef.current += 1;
    setMessages([]);
    setInput('');
    setLoading(false);
    setHasStartedChat(false);
    setErrorMessage(null);
    setLastFailedPrompt(null);
    setLastFailedMessageId(null);
  };

  /* Reopening (or switching screens while open) must never show the previous conversation. */
  useEffect(() => {
    if (!isOpen) {
      resetConversation();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen]);

  useEffect(() => {
    resetConversation();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [context.route, context.regionAnalysisId, context.fieldId, context.reportDate, visibleDataKey]);

  useEffect(() => {
    if (messages.length > 0) {
      chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages]);

  const handleSendPrompt = async (promptText: string, retryMessageId?: string) => {
    if (!promptText.trim() || loading) return;
    setHasStartedChat(true);
    setErrorMessage(null);
    setLastFailedPrompt(null);
    setLastFailedMessageId(null);
    const userMsg: Message | undefined = retryMessageId
      ? messages.find(message => message.id === retryMessageId && message.sender === 'user')
      : {
          id: crypto.randomUUID(),
          sender: 'user',
          content: promptText,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
    if (!retryMessageId && userMsg) setMessages(prev => [...prev, userMsg]);
    setInput('');
    setLoading(true);
    const history = (retryMessageId ? messages.filter(message => message.id !== retryMessageId) : messages)
      .slice(-8)
      .map(message => ({ role: message.sender, content: message.content }));

    const abortController = new AbortController();
    abortControllerRef.current = abortController;
    const requestSessionId = sessionIdRef.current;

    try {
      const response = await ApiService.sendChatMessage({
        message: promptText,
        history,
        context: {
          regionAnalysisId: context.regionAnalysisId,
          fieldId: context.fieldId,
          reportDate: context.reportDate,
          route: context.route,
          visibleData: context.visibleData
        }
      });
      // A reset/close/context-change may have happened while this request was in flight.
      if (requestSessionId !== sessionIdRef.current) return;
      const aiMsg: Message = {
        id: crypto.randomUUID(),
        sender: 'assistant',
        content: response.answer?.answer ?? '답변을 생성하지 못했습니다.',
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        sources: response.sources,
        grounded: response.status === 'completed'
      };
      setMessages(prev => [...prev, aiMsg]);
    } catch (err) {
      if (requestSessionId !== sessionIdRef.current) return;
      setErrorMessage(err instanceof ApiError ? err.message : 'AI 답변을 불러오지 못했습니다. 다시 시도해 주세요.');
      setLastFailedPrompt(promptText);
      setLastFailedMessageId(userMsg?.id ?? null);
    } finally {
      if (requestSessionId === sessionIdRef.current) setLoading(false);
    }
  };

  const handleRetry = () => {
    if (lastFailedPrompt) void handleSendPrompt(lastFailedPrompt, lastFailedMessageId ?? undefined);
  };

  const handleReset = () => {
    resetConversation();
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
                      src="/assets/ai-chat-mascot.png"
                      alt="Farmflate AI 캐릭터"
                      style={{ width: 150, height: 100, objectFit: 'contain', marginBottom: 6 }}
                    />
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
                    <img src="/assets/brand-wordmark-new.png" alt="Farmflate" style={{ height: 18 }} />
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
                      <div style={{ overflowWrap: 'anywhere', wordBreak: 'break-word' }}>
                        {msg.content.split('\n').map((line, index) => {
                          const trimmed = line.trim();
                          const isHeading = ['핵심 판단', '근거', '지금 할 일'].includes(trimmed);
                          const isAction = /^\d+\.\s/.test(trimmed);
                          if (isHeading) {
                            return (
                              <strong key={`${msg.id}-line-${index}`} style={{ display: 'block', marginTop: index === 0 ? 0 : 12, color: '#1E7A43', fontSize: '0.84rem' }}>
                                {trimmed}
                              </strong>
                            );
                          }
                          if (isAction) {
                            return <div key={`${msg.id}-line-${index}`} style={{ marginTop: 6, paddingLeft: 2 }}>{trimmed}</div>;
                          }
                          return <p key={`${msg.id}-line-${index}`} style={{ margin: trimmed ? '5px 0 0' : '5px 0', whiteSpace: 'pre-wrap' }}>{trimmed || '\u00A0'}</p>;
                        })}
                      </div>
                      {msg.sender === 'assistant' && msg.sources && msg.sources.length > 0 && (
                        <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid #E2E8F0', fontSize: '0.7rem', color: '#667085', lineHeight: 1.45 }}>
                          <strong style={{ color: '#2e9f5b' }}>답변 근거</strong>
                          {msg.sources.slice(0, 2).map((source, index) => (
                            <div key={`${msg.id}-source-${index}`} style={{ marginTop: 2 }}>
                              {source.title}{source.detail ? ` · ${source.detail}` : ''}
                            </div>
                          ))}
                        </div>
                      )}
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
                  {errorMessage && (
                    <div role="alert" style={{ alignSelf: 'flex-start', maxWidth: '85%', padding: '12px 16px', backgroundColor: '#FFF4F0', border: '1px solid #FFD5C8', borderRadius: 18, fontSize: '0.84rem', color: '#B54708', lineHeight: 1.5 }}>
                      <div style={{ marginBottom: lastFailedPrompt ? 8 : 0 }}>{errorMessage}</div>
                      {lastFailedPrompt && (
                        <button onClick={handleRetry} style={{ background: 'none', border: 'none', color: '#B54708', fontWeight: 800, fontSize: '0.8rem', cursor: 'pointer', padding: 0, textDecoration: 'underline' }}>
                          다시 보내기
                        </button>
                      )}
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
