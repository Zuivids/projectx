import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AppRoutes } from './App'
import { authApi, type Me } from './api/auth'
import { api, ApiError } from './api/client'
import { AuthProvider } from './auth/AuthContext'

const learner: Me = {
  email: 'anna@example.com',
  displayName: 'Anna',
  roles: ['LEARNER'],
  mfaEnabled: false,
  mfaVerified: false,
  mfaSetupRequired: false,
}

const admin: Me = { ...learner, displayName: 'Admins', roles: ['ADMIN', 'LEARNER'], mfaEnabled: true }

function loggedInAs(user: Me | null) {
  vi.spyOn(authApi, 'me').mockImplementation(async () => {
    if (!user) throw new ApiError(401, 'Unauthorized')
    return user
  })
}

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('page access', () => {
  it('shows the landing page to visitors', async () => {
    loggedInAs(null)
    renderAt('/')
    expect(await screen.findByRole('link', { name: 'Sākt mācīties' })).toBeInTheDocument()
  })

  it('sends visitors from a private page to login', async () => {
    loggedInAs(null)
    renderAt('/sakums')
    expect(await screen.findByRole('heading', { name: 'Pieslēgties' })).toBeInTheDocument()
  })

  it('shows the home page to a logged-in learner', async () => {
    loggedInAs(learner)
    renderAt('/sakums')
    expect(await screen.findByRole('heading', { name: 'Labdien, Anna!' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Administrēšana' })).not.toBeInTheDocument()
  })

  it('keeps learners out of the admin panel', async () => {
    loggedInAs(learner)
    renderAt('/admin')
    expect(await screen.findByRole('heading', { name: 'Labdien, Anna!' })).toBeInTheDocument()
  })

  it('sends an admin without MFA in this session to the security page', async () => {
    loggedInAs(admin)
    renderAt('/admin')
    expect(await screen.findByText(/nepieciešama divpakāpju pieslēgšanās šajā sesijā/)).toBeInTheDocument()
  })

  it('opens the admin panel for an admin who passed MFA', async () => {
    loggedInAs({ ...admin, mfaVerified: true })
    renderAt('/admin')
    expect(await screen.findByRole('heading', { name: 'Administrēšana' })).toBeInTheDocument()
  })
})

describe('session ended by the server', () => {
  it('sends the user to login with a notice when an API call gets 401', async () => {
    let sessionAlive = true
    vi.spyOn(authApi, 'me').mockImplementation(async () => {
      if (!sessionAlive) throw new ApiError(401, 'Unauthorized')
      return learner
    })
    renderAt('/konts/drosiba')
    expect(await screen.findByRole('heading', { name: 'Drošība' })).toBeInTheDocument()

    // The server ends the session (e.g. timeout); the next API call answers 401
    sessionAlive = false
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 401 })))
    await api('/api/v1/me/mfa/totp', { method: 'POST' }).catch(() => undefined)

    expect(await screen.findByRole('heading', { name: 'Pieslēgties' })).toBeInTheDocument()
    expect(screen.getByText('Sesija beidzās. Lūdzu, pieslēdzies vēlreiz.')).toBeInTheDocument()
  })

  it('does not treat a wrong password as an ended session', async () => {
    loggedInAs(null)
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 401 })))
    renderAt('/pieslegties')
    await screen.findByRole('heading', { name: 'Pieslēgties' })

    await api('/api/v1/auth/login', { method: 'POST', body: {} }).catch(() => undefined)

    expect(screen.queryByText(/Sesija beidzās/)).not.toBeInTheDocument()
  })
})

describe('logout', () => {
  it('goes to the landing page, not the login page', async () => {
    let loggedIn = true
    vi.spyOn(authApi, 'me').mockImplementation(async () => {
      if (!loggedIn) throw new ApiError(401, 'Unauthorized')
      return learner
    })
    vi.spyOn(authApi, 'logout').mockImplementation(async () => {
      loggedIn = false
    })
    const user = userEvent.setup()
    renderAt('/konts/drosiba')

    await user.click(await screen.findByRole('button', { name: 'Iziet' }))

    expect(await screen.findByRole('link', { name: 'Sākt mācīties' })).toBeInTheDocument()
  })
})

describe('login', () => {
  it('asks for a code when MFA is required, then logs in', async () => {
    let loggedIn = false
    vi.spyOn(authApi, 'me').mockImplementation(async () => {
      if (!loggedIn) throw new ApiError(401, 'Unauthorized')
      return learner
    })
    vi.spyOn(authApi, 'login').mockResolvedValue({ status: 'MFA_REQUIRED', mfaSetupRequired: false })
    const completeMfa = vi.spyOn(authApi, 'completeMfa').mockImplementation(async () => {
      loggedIn = true
      return { status: 'AUTHENTICATED', mfaSetupRequired: false }
    })
    const user = userEvent.setup()
    renderAt('/pieslegties')

    await user.type(await screen.findByLabelText('E-pasts'), 'anna@example.com')
    await user.type(screen.getByLabelText('Parole'), 'correct horse battery')
    await user.click(screen.getByRole('button', { name: 'Pieslēgties' }))

    await user.type(await screen.findByLabelText('Kods'), '123456')
    await user.click(screen.getByRole('button', { name: 'Apstiprināt' }))

    expect(completeMfa).toHaveBeenCalledWith('123456')
    expect(await screen.findByRole('heading', { name: 'Labdien, Anna!' })).toBeInTheDocument()
  })

  it('shows a Latvian message for a wrong password', async () => {
    loggedInAs(null)
    vi.spyOn(authApi, 'login').mockRejectedValue(new ApiError(401, 'Invalid email or password'))
    const user = userEvent.setup()
    renderAt('/pieslegties')

    await user.type(await screen.findByLabelText('E-pasts'), 'anna@example.com')
    await user.type(screen.getByLabelText('Parole'), 'wrong password')
    await user.click(screen.getByRole('button', { name: 'Pieslēgties' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Nepareizs e-pasts vai parole')
  })
})
