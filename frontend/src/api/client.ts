import axios from 'axios';
import { useAuthStore } from '../store/authStore';

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

// 请求拦截器：注入 JWT + Accept-Language
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['Accept-Language'] = useAuthStore.getState().locale;
  return config;
});

// 响应拦截器：处理 401 自动刷新
apiClient.interceptors.response.use(
  (resp) => resp,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true;
      const refreshToken = useAuthStore.getState().refreshToken;
      if (refreshToken) {
        try {
          const { data } = await axios.post('/api/auth/refresh', { refreshToken });
          useAuthStore.getState().setAuth(
            data.data.accessToken,
            data.data.refreshToken,
            useAuthStore.getState().userId!,
            useAuthStore.getState().tenantId!
          );
          original.headers.Authorization = `Bearer ${data.data.accessToken}`;
          return apiClient(original);
        } catch {
          useAuthStore.getState().clearAuth();
          window.location.href = '/login';
        }
      } else {
        useAuthStore.getState().clearAuth();
        window.location.href = '/login';
      }
    }
    const backendData = error.response?.data;
    if (backendData && backendData.message) {
      const err = new Error(backendData.message);
      (err as any).code = backendData.code;
      return Promise.reject(err);
    }
    return Promise.reject(error);
  }
);

export default apiClient;
