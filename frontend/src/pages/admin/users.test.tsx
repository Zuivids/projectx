import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../../App'
import { authApi, type Me } from '../../api/auth'
import { ApiError } from '../../api/client'
import { userAdminApi, type AdminUserDetail } from '../../api/users'
import { AuthProvider } from '../../auth/AuthContext'

const adminMe: Me = {
  email: 'admin@example.com',
  displayName: 'Admins',
  roles: ['ADMIN', 'LEARNER'],
  mfaEnabled: true,
  mfaVerified: true,
  mfaSetupRequired: false,
}

const superMe: Me = { ...adminMe, email: 'super@example.com', roles: ['LEARNER', 'SUPER_ADMIN'] }

function detail(overrides: Partial<AdminUserDetail> = {}): AdminUserDetail {
  return {
    id: 5,
    email: 'anna@example.com',
    displayName: 'Anna',
    roles: ['LEARNER'],
    status: 'ACTIVE',
    mfaEnabled: true,
    recoveryCodesLeft: 9,
    lockedUntil: null,
    createdAt: '2026-10-01T09:00:00Z',
    activity: [
      { occurredAt: '2026-10-20T08:00:00Z', type: 'LOGIN_SUCCEEDED', ipAddress: '10.0.0.1', details: null, byAdmin: false, otherUserId: null },
    ],
    ...overrides,
  }
}

function renderAt(path: string, me: Me) {
  vi.spyOn(authApi, 'me').mockResolvedValue(me)
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('admin user list', () => {
  it('searches by the typed text', async () => {
    const list = vi.spyOn(userAdminApi, 'list').mockResolvedValue({
      items: [{ id: 5, email: 'anna@example.com', displayName: 'Anna', roles: ['LEARNER'], status: 'ACTIVE', mfaEnabled: false, locked: true }],
      page: 0,
      size: 50,
      totalItems: 1,
      totalPages: 1,
    })
    const user = userEvent.setup()
    renderAt('/admin/lietotaji', adminMe)

    expect(await screen.findByRole('link', { name: 'anna@example.com' })).toHaveAttribute('href', '/admin/lietotaji/5')
    expect(screen.getByText('Bloķēts pēc kļūdām')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Meklēt lietotāju'), 'anna')
    await user.click(screen.getByRole('button', { name: 'Meklēt' }))
    expect(list).toHaveBeenLastCalledWith('anna', undefined)
  })
})

describe('admin user page', () => {
  it('lets an admin block a learner after confirming', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(detail())
    const disable = vi.spyOn(userAdminApi, 'disable').mockResolvedValue(detail({ status: 'DISABLED' }))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderAt('/admin/lietotaji/5', adminMe)

    await user.click(await screen.findByRole('button', { name: 'Bloķēt kontu' }))

    expect(disable).toHaveBeenCalledWith(5)
    expect(await screen.findByRole('button', { name: 'Atbloķēt kontu' })).toBeInTheDocument()
  })

  it('does nothing when the confirmation is cancelled', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(detail())
    const resetMfa = vi.spyOn(userAdminApi, 'resetMfa')
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = userEvent.setup()
    renderAt('/admin/lietotaji/5', adminMe)

    await user.click(await screen.findByRole('button', { name: 'Atiestatīt MFA' }))

    expect(resetMfa).not.toHaveBeenCalled()
  })

  it('shows role buttons only to super admins', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(detail())
    renderAt('/admin/lietotaji/5', adminMe)
    await screen.findByRole('heading', { name: 'Anna' })
    expect(screen.queryByRole('button', { name: /Piešķirt/ })).not.toBeInTheDocument()
  })

  it('lets a super admin grant the admin role', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(detail())
    const grant = vi.spyOn(userAdminApi, 'grantRole').mockResolvedValue(detail({ roles: ['ADMIN', 'LEARNER'] }))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderAt('/admin/lietotaji/5', superMe)

    await user.click(await screen.findByRole('button', { name: 'Piešķirt: Administrators' }))

    expect(grant).toHaveBeenCalledWith(5, 'ADMIN')
    expect(await screen.findByRole('button', { name: 'Noņemt: Administrators' })).toBeInTheDocument()
  })

  it('hides actions on admin accounts from a regular admin', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(detail({ roles: ['ADMIN', 'LEARNER'] }))
    renderAt('/admin/lietotaji/5', adminMe)

    expect(await screen.findByText(/var pārvaldīt tikai galvenais administrators/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bloķēt kontu' })).not.toBeInTheDocument()
  })

  it('shows no actions on your own account', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(detail({ email: 'admin@example.com', roles: ['ADMIN', 'LEARNER'] }))
    renderAt('/admin/lietotaji/5', adminMe)

    expect(await screen.findByText(/Tas ir tavs konts/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Bloķēt kontu' })).not.toBeInTheDocument()
  })

  it('explains a refusal from the server in Latvian', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(detail())
    vi.spyOn(userAdminApi, 'disable').mockRejectedValue(
      new ApiError(403, 'Only a super admin can manage admin accounts'),
    )
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderAt('/admin/lietotaji/5', adminMe)

    await user.click(await screen.findByRole('button', { name: 'Bloķēt kontu' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('tikai galvenais administrators')
  })

  it('marks actions this admin took on other accounts', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(
      detail({
        activity: [
          { occurredAt: '2026-10-20T08:00:00Z', type: 'USER_DISABLED', ipAddress: null, details: null, byAdmin: false, otherUserId: 9 },
        ],
      }),
    )
    renderAt('/admin/lietotaji/5', adminMe)

    expect(await screen.findByText(/Šis lietotājs citam kontam/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '#9' })).toHaveAttribute('href', '/admin/lietotaji/9')
  })

  it('shows the activity in Latvian', async () => {
    vi.spyOn(userAdminApi, 'get').mockResolvedValue(detail())
    renderAt('/admin/lietotaji/5', adminMe)
    expect(await screen.findByText('Pieslēdzās')).toBeInTheDocument()
    expect(screen.getByText('10.0.0.1')).toBeInTheDocument()
  })
})
