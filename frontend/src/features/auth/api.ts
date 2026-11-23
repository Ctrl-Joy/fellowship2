import { http } from '@/services/apiClient';
import type { AuthResponse, SessionInfo } from '@/types/api';
import type { VerifyEmailResult } from './verifyOutcome';

export const authApi = {
  login: (body: { login: string; password: string }) =>
    http.post<AuthResponse>('/auth/login', body, { skipRefresh: true }),
  register: (body: { username: string; email: string; password: string; displayName?: string }) =>
    http.post<AuthResponse>('/auth/register', body, { skipRefresh: true }),
  logout: () => http.post<void>('/auth/logout', undefined, { skipRefresh: true }),
  usernameAvailable: (username: string, signal?: AbortSignal) =>
    http.get<{ available: boolean; reason: string | null }>('/auth/username-available', { username }, signal),
  verifyEmail: (token: string) => http.post<VerifyEmailResult>('/auth/verify-email', { token }, { skipRefresh: true }),
  resendVerification: () => http.post<void>('/auth/resend-verification'),
  forgotPassword: (email: string) => http.post<void>('/auth/forgot-password', { email }, { skipRefresh: true }),
  resetPassword: (token: string, newPassword: string) =>
    http.post<void>('/auth/reset-password', { token, newPassword }, { skipRefresh: true }),
  sessions: () => http.get<SessionInfo[]>('/auth/sessions'),
  revokeSession: (id: number) => http.del<void>(`/auth/sessions/${id}`),
};
