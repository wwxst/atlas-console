import { create } from 'zustand'

type ThemeMode = 'light' | 'dark'
interface AppState {
  sidebarCollapsed: boolean
  theme: ThemeMode
  toggleSidebar: () => void
  setTheme: (theme: ThemeMode) => void
}

export const useAppStore = create<AppState>((set) => ({
  sidebarCollapsed: false,
  theme: 'light',
  toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
  setTheme: (theme) => set({ theme }),
}))
