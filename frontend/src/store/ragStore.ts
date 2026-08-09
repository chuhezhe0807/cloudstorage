import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  citations?: { fileId: string; fileName: string; snippet: string }[];
}

interface RagState {
  sessionId: string | null;
  kbId: string | null;
  messages: Message[];
  setSession: (sessionId: string) => void;
  setKbId: (kbId: string) => void;
  addMessage: (msg: Message) => void;
  clearMessages: () => void;
}

export const useRagStore = create<RagState>()(
  persist(
    (set) => ({
      sessionId: null,
      kbId: null,
      messages: [],
      setSession: (sessionId) => set({ sessionId }),
      setKbId: (kbId) => set({ kbId }),
      addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
      clearMessages: () => set({ messages: [] }),
    }),
    { name: 'rag-storage' }
  )
);
