import { api } from './client'
import type { Page } from './courses'

export type Role = 'LEARNER' | 'ADMIN' | 'SUPER_ADMIN'
export type UserStatus = 'ACTIVE' | 'DISABLED'

/** Mirrors lv.lms.user.admin.UserAdminResponses */
export interface AdminUserSummary {
  id: number
  email: string
  displayName: string
  roles: Role[]
  status: UserStatus
  mfaEnabled: boolean
  locked: boolean
}

export interface UserActivity {
  occurredAt: string
  type: string
  ipAddress: string | null
  details: string | null
  byAdmin: boolean
  /** Set when this user, as an admin, acted on another account */
  otherUserId: number | null
}

export interface AdminUserDetail {
  id: number
  email: string
  displayName: string
  roles: Role[]
  status: UserStatus
  mfaEnabled: boolean
  recoveryCodesLeft: number
  lockedUntil: string | null
  createdAt: string
  activity: UserActivity[]
}

const USERS = '/api/v1/admin/users'

export const userAdminApi = {
  list: (query: string, status?: UserStatus, page = 0) => {
    const params = new URLSearchParams({ page: String(page), size: '50' })
    if (query.trim()) params.set('query', query.trim())
    if (status) params.set('status', status)
    return api<Page<AdminUserSummary>>(`${USERS}?${params}`)
  },
  get: (id: number) => api<AdminUserDetail>(`${USERS}/${id}`),
  disable: (id: number) => api<AdminUserDetail>(`${USERS}/${id}/disable`, { method: 'POST' }),
  enable: (id: number) => api<AdminUserDetail>(`${USERS}/${id}/enable`, { method: 'POST' }),
  unlock: (id: number) => api<AdminUserDetail>(`${USERS}/${id}/unlock`, { method: 'POST' }),
  resetMfa: (id: number) => api<AdminUserDetail>(`${USERS}/${id}/reset-mfa`, { method: 'POST' }),
  grantRole: (id: number, role: Role) => api<AdminUserDetail>(`${USERS}/${id}/roles/${role}`, { method: 'PUT' }),
  revokeRole: (id: number, role: Role) =>
    api<AdminUserDetail>(`${USERS}/${id}/roles/${role}`, { method: 'DELETE' }),
}

export const isStaff = (roles: Role[]) => roles.some((r) => r === 'ADMIN' || r === 'SUPER_ADMIN')
