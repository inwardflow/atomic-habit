import axios from 'axios';
import { useAuthStore } from '../store/authStore';
import toast from 'react-hot-toast';
import i18n from '../i18n';

/** API base URL from environment variable, defaults to localhost for development */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

/** Backend root URL (for AG-UI, SSE, etc.) */
export const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || '';

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['Accept-Language'] = i18n.language;
  return config;
});

let refreshPromise: Promise<string> | null = null;

/**
 * Exchanges the HttpOnly refresh cookie for a new access token.
 *
 * Single-flight: refresh tokens are rotated (single use) on the server, so concurrent callers
 * (app start-up under React StrictMode, several 401s at once) must share one request; a second
 * request with the same cookie would be rejected and log the user out.
 */
export const refreshAccessToken = (): Promise<string> => {
  if (!refreshPromise) {
    refreshPromise = api
      .post('/auth/refresh-token', undefined, { _skipAuthRefresh: true } as object)
      .then((response) => {
        const { accessToken } = response.data as { accessToken: string };
        useAuthStore.getState().setToken(accessToken);
        return accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const url: string = originalRequest?.url ?? '';

    if (
      error.response?.status !== 401 ||
      originalRequest._retry ||
      originalRequest._skipAuthRefresh ||
      url.includes('/auth/login')
    ) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;
    try {
      const accessToken = await refreshAccessToken();
      originalRequest.headers['Authorization'] = 'Bearer ' + accessToken;
      return api(originalRequest);
    } catch (err) {
      if (useAuthStore.getState().token) {
        useAuthStore.getState().logout();
        toast.error(i18n.t('auth.session_expired'));
      }
      return Promise.reject(err);
    }
  }
);

export default api;
