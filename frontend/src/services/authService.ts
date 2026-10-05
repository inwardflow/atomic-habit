
import api, { refreshAccessToken } from '../api/axios';
import { useAuthStore } from '../store/authStore';
import type { Session, LoginHistory } from '../types/authTypes';

const getDeviceId = () => {
    let deviceId = localStorage.getItem('device_id');
    if (!deviceId) {
        // Use crypto.randomUUID if available, otherwise fallback to a simple random string
        if (typeof crypto !== 'undefined' && crypto.randomUUID) {
            deviceId = crypto.randomUUID();
        } else {
            deviceId = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
        }
        localStorage.setItem('device_id', deviceId);
    }
    return deviceId;
};

export interface LoginCredentials {
    email: string;
    password: string;
    deviceId?: string;
}

export interface RegisterData {
    email: string;
    password: string;
    identityStatement?: string;
}

/** Loads the signed-in user's profile into the auth store (pages and the coach rely on it). */
const loadCurrentUser = async () => {
    try {
        const response = await api.get('/users/me');
        useAuthStore.getState().setUser(response.data);
    } catch {
        // Non-fatal: pages that need the profile fetch it again.
    }
};

export const authService = {
    // ... existing methods
    login: async (credentials: LoginCredentials) => {
        const deviceId = getDeviceId();
        const response = await api.post('/auth/login', { ...credentials, deviceId });
        const { accessToken } = response.data;
        useAuthStore.getState().setToken(accessToken);
        await loadCurrentUser();
        return response.data;
    },
    
    register: async (data: RegisterData) => {
        return api.post('/auth/register', data);
    },

    logout: async () => {
        try {
            await api.post('/auth/logout');
        } finally {
            useAuthStore.getState().logout();
        }
    },
    
    logoutAll: async () => {
        try {
            await api.post('/auth/logout-all');
        } finally {
            useAuthStore.getState().logout();
        }
    },

    checkAuth: async () => {
        try {
            await refreshAccessToken();
            await loadCurrentUser();
        } catch {
            useAuthStore.getState().logout();
        } finally {
            useAuthStore.getState().setLoading(false);
        }
    },
    
    getSessions: async (): Promise<Session[]> => {
        const response = await api.get('/auth/sessions');
        return response.data;
    },
    
    revokeSession: async (sessionId: number) => {
        await api.delete(`/auth/sessions/${sessionId}`);
    },
    
    getLoginHistory: async (): Promise<LoginHistory[]> => {
        const response = await api.get('/auth/login-history');
        return response.data;
    }
};
