import { create } from 'zustand';
import { persist } from 'zustand/middleware';

type Theme = 'light' | 'dark';
type Lang = 'zh' | 'en';

interface ThemeState {
  theme: Theme;
  lang: Lang;
  setTheme: (theme: Theme) => void;
  setLang: (lang: Lang) => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      theme: 'light',
      lang: 'zh',
      setTheme: (theme) => set({ theme }),
      setLang: (lang) => set({ lang }),
    }),
    { name: 'theme-storage' }
  )
);
