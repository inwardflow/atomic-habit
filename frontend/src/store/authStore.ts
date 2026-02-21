import { create } from 'zustand';

interface User {
  id?: number;
  email: string;
  identityStatement: string;
  roles: string[];
}

interface AuthState {
  token: string | null;
  user: User | null;
  isLoading: boolean;
  setToken: (token: string | null) => void;
  setUser: (user: User | null) => void;
  setLoading: (loading: boolean) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null, // No localStorage
  user: null,
  isLoading: true, // Start with loading true to check auth on mount
  setToken: (token) => set({ token }),
  setUser: (user) => set({ user }),
  setLoading: (isLoading) => set({ isLoading }),
  logout: () => set({ token: null, user: null }),
}));
