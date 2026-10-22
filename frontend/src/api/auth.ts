import { api } from './client'

/** Mirrors lv.lms.user.dto.MeResponse */
export interface Me {
  email: string
  displayName: string
  roles: string[]
  mfaEnabled: boolean
  mfaVerified: boolean
  mfaSetupRequired: boolean
}

/** Mirrors lv.lms.auth.dto.LoginResponse */
export interface LoginResponse {
  status: 'AUTHENTICATED' | 'MFA_REQUIRED'
  mfaSetupRequired: boolean
}

export interface TotpSetup {
  secret: string
  otpauthUri: string
}

export const authApi = {
  me: () => api<Me>('/api/v1/me'),

  register: (email: string, password: string, displayName: string) =>
    api<void>('/api/v1/auth/register', { method: 'POST', body: { email, password, displayName } }),

  login: (email: string, password: string) =>
    api<LoginResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password } }),

  completeMfa: (code: string) => api<LoginResponse>('/api/v1/auth/mfa', { method: 'POST', body: { code } }),

  logout: () => api<void>('/api/v1/auth/logout', { method: 'POST' }),

  startTotpSetup: () => api<TotpSetup>('/api/v1/me/mfa/totp', { method: 'POST' }),

  confirmTotpSetup: (code: string) =>
    api<{ recoveryCodes: string[] }>('/api/v1/me/mfa/totp/confirm', { method: 'POST', body: { code } }),
}

export function isAdmin(me: Me | null): boolean {
  return !!me && me.roles.some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN')
}
