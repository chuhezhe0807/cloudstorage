import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  userId: number | null;
  tenantId: number | null;
  locale: string;
  setAuth: (accessToken: string, refreshToken: string, userId: number, tenantId: number, locale?: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      userId: null,
      tenantId: null,
      locale: 'zh',
      setAuth: (accessToken, refreshToken, userId, tenantId, locale = 'zh') =>
        set({ accessToken, refreshToken, userId, tenantId, locale }),
      clearAuth: () =>
        set({ accessToken: null, refreshToken: null, userId: null, tenantId: null }),
    }),
    { name: 'auth-storage' }
  )
);
