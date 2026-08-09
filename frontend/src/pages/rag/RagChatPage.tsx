import { useState, useEffect, useRef } from 'react';
import { Button, Input, Select, Space, Spin } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import apiClient from '../../api/client';
import { useRagStore } from '../../store/ragStore';

interface Citation {
  fileId: string;
  fileName: string;
  snippet: string;
}

interface KbOption {
  id: string;
  name: string;
}

export default function RagChatPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [kbs, setKbs] = useState<KbOption[]>([]);
  const chatRef = useRef<HTMLDivElement>(null);

  const { sessionId, kbId, messages, setSession, setKbId, addMessage, clearMessages } = useRagStore();

  useEffect(() => {
    apiClient.get('/rag/kbs').then(({ data }) => {
      setKbs((data.data || []).map((kb: { id: string; name: string }) => ({ id: kb.id, name: kb.name })));
    });
  }, []);

  useEffect(() => {
    const initKbId = searchParams.get('kbId');
    if (initKbId && !kbId) {
      setKbId(initKbId);
    }
  }, [searchParams, kbId, setKbId]);

  useEffect(() => {
    chatRef.current?.scrollTo(0, chatRef.current.scrollHeight);
  }, [messages]);

  const handleSend = async () => {
    if (!input.trim() || !kbId) return;
    const msg = input.trim();
    setInput('');
    addMessage({ role: 'user', content: msg });
    setLoading(true);

    try {
      const { data: res } = await apiClient.post('/rag/chat', {
        kbId,
        message: msg,
        sessionId: sessionId,
      });
      const answer: string = res.data.answer;
      const citations: Citation[] = res.data.citations || [];
      const sid: string = res.data.sessionId;
      if (sid) setSession(sid);
      addMessage({ role: 'assistant', content: answer, citations });
    } catch {
      addMessage({ role: 'assistant', content: t('error.llm_call_failed') || 'LLM call failed' });
    }
    setLoading(false);
  };

  const handleKbChange = (newKbId: string) => {
    setKbId(newKbId);
    clearMessages();
    setSession('');
  };

  return (
    <div className="flex flex-col h-[calc(100vh-120px)]">
      <h2 className="text-xl font-bold mb-3">{t('rag.title')}</h2>
      <div className="mb-3">
        <Select
          placeholder={t('rag.selectKb')}
          value={kbId || undefined}
          onChange={handleKbChange}
          options={kbs.map((kb) => ({ value: kb.id, label: kb.name }))}
          className="w-72"
        />
      </div>
      <div ref={chatRef} className="flex-1 overflow-y-auto mb-4 border rounded p-4">
        {messages.map((msg, i) => (
          <div key={i} className={`mb-4 ${msg.role === 'user' ? 'text-right' : 'text-left'}`}>
            <div className={`inline-block max-w-[80%] rounded-lg px-4 py-2 ${msg.role === 'user' ? 'bg-blue-500 text-white' : 'bg-gray-100 dark:bg-gray-700'}`}>
              <div className="whitespace-pre-wrap">{msg.content}</div>
              {msg.citations && msg.citations.length > 0 && (
                <div className="mt-2 pt-2 border-t text-xs">
                  <div className="font-semibold mb-1">{t('rag.citations')}:</div>
                  {msg.citations.map((c, j) => (
                    <div key={j} className="mb-1">
                      <span className="font-medium">{c.fileName}</span>
                      <span className="text-gray-500 ml-2">{c.snippet}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
        {loading && <Spin />}
      </div>
      <Space.Compact className="w-full">
        <Input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onPressEnter={handleSend}
          placeholder={t('rag.inputPlaceholder')}
          disabled={!kbId || loading}
        />
        <Button type="primary" icon={<SendOutlined />} onClick={handleSend} loading={loading} disabled={!kbId}>
          {t('rag.send')}
        </Button>
      </Space.Compact>
    </div>
  );
}
