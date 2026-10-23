import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import { authApi, type Me } from '../api/auth'
import { ApiError } from '../api/client'
import { catalogApi, courseAdminApi, type AdminCourse, type CatalogCourseDetail } from '../api/courses'
import { AuthProvider } from '../auth/AuthContext'

const admin: Me = {
  email: 'admin@example.com',
  displayName: 'Admins',
  roles: ['ADMIN', 'LEARNER'],
  mfaEnabled: true,
  mfaVerified: true,
  mfaSetupRequired: false,
}

const publishedCourse: CatalogCourseDetail = {
  slug: 'latviesu-valoda',
  title: 'Latviešu valoda',
  description: 'Apraksts\nar divām rindām',
  priceCents: 4900,
  currency: 'EUR',
  level: 'BEGINNER',
  lessonCount: 2,
  totalDurationSeconds: 900,
  sections: [
    {
      title: 'Ievads',
      lessons: [
        { id: 1, title: 'Sveiciens', type: 'AUDIO', durationSeconds: 300, preview: true },
        { id: 2, title: 'Gramatika', type: 'TEXT', durationSeconds: 600, preview: false },
      ],
    },
  ],
}

function draftCourse(overrides: Partial<AdminCourse> = {}): AdminCourse {
  return {
    id: 7,
    title: 'Jauns kurss',
    slug: 'jauns-kurss',
    description: '',
    priceCents: 4990,
    currency: 'EUR',
    level: null,
    status: 'DRAFT',
    version: 0,
    publishedAt: null,
    updatedAt: '2026-10-23T10:00:00Z',
    canBeDeleted: true,
    sections: [],
    ...overrides,
  }
}

function renderAt(path: string, user: Me | null = null) {
  vi.spyOn(authApi, 'me').mockImplementation(async () => {
    if (!user) throw new ApiError(401, 'Unauthorized')
    return user
  })
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('public catalog', () => {
  it('lists published courses with price and lesson count', async () => {
    vi.spyOn(catalogApi, 'list').mockResolvedValue({
      items: [publishedCourse],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    })
    renderAt('/kursi')

    const card = await screen.findByRole('link', { name: /Latviešu valoda/ })
    expect(card).toHaveAttribute('href', '/kursi/latviesu-valoda')
    expect(within(card).getByText('2 nodarbības')).toBeInTheDocument()
    expect(within(card).getByText(/49,00/)).toBeInTheDocument()
  })

  it('shows the table of contents and links only free lessons', async () => {
    vi.spyOn(catalogApi, 'get').mockResolvedValue(publishedCourse)
    renderAt('/kursi/latviesu-valoda')

    expect(await screen.findByRole('heading', { name: 'Latviešu valoda' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sveiciens' })).toHaveAttribute(
      'href',
      '/kursi/latviesu-valoda/nodarbibas/1',
    )
    expect(screen.queryByRole('link', { name: 'Gramatika' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pirkt' })).toBeDisabled()
  })

  it('shows "not found" for an unknown course', async () => {
    vi.spyOn(catalogApi, 'get').mockRejectedValue(new ApiError(404, 'Course not found'))
    renderAt('/kursi/nav-tada')
    expect(await screen.findByRole('heading', { name: 'Lapa nav atrasta' })).toBeInTheDocument()
  })

  it('explains that paid lessons need a purchase', async () => {
    vi.spyOn(catalogApi, 'previewLesson').mockRejectedValue(new ApiError(403, 'Forbidden'))
    renderAt('/kursi/latviesu-valoda/nodarbibas/2')
    expect(await screen.findByText('Šī nodarbība būs pieejama pēc kursa iegādes.')).toBeInTheDocument()
  })
})

describe('admin courses', () => {
  it('creates a course and opens the editor', async () => {
    vi.spyOn(courseAdminApi, 'list').mockResolvedValue({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 })
    const create = vi.spyOn(courseAdminApi, 'create').mockResolvedValue(draftCourse())
    vi.spyOn(courseAdminApi, 'get').mockResolvedValue(draftCourse())
    const user = userEvent.setup()
    renderAt('/admin/kursi', admin)

    await user.click(await screen.findByRole('button', { name: 'Jauns kurss' }))
    await user.type(screen.getByLabelText(/Nosaukums/), 'Jauns kurss')
    await user.type(screen.getByLabelText('Cena, €'), '49,90')
    await user.click(screen.getByRole('button', { name: 'Izveidot' }))

    expect(create).toHaveBeenCalledWith({ title: 'Jauns kurss', description: '', priceCents: 4990, level: null })
    expect(await screen.findByRole('heading', { name: 'Kursa informācija' })).toBeInTheDocument()
  })

  it('refuses a price that is not a euro amount', async () => {
    vi.spyOn(courseAdminApi, 'list').mockResolvedValue({ items: [], page: 0, size: 50, totalItems: 0, totalPages: 0 })
    const create = vi.spyOn(courseAdminApi, 'create')
    const user = userEvent.setup()
    renderAt('/admin/kursi', admin)

    await user.click(await screen.findByRole('button', { name: 'Jauns kurss' }))
    await user.type(screen.getByLabelText(/Nosaukums/), 'Kurss')
    await user.type(screen.getByLabelText('Cena, €'), '49 eiro')
    await user.click(screen.getByRole('button', { name: 'Izveidot' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Cenu ievadi eiro')
    expect(create).not.toHaveBeenCalled()
  })

  it('shows a Latvian explanation when publishing an empty course fails', async () => {
    vi.spyOn(courseAdminApi, 'get').mockResolvedValue(draftCourse())
    vi.spyOn(courseAdminApi, 'publish').mockRejectedValue(
      new ApiError(409, 'A course needs at least one lesson before it can be published'),
    )
    const user = userEvent.setup()
    renderAt('/admin/kursi/7', admin)

    await user.click(await screen.findByRole('button', { name: 'Publicēt' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('jāpievieno vismaz viena nodarbība')
  })

  it('saves details with the loaded version and shows the saved course', async () => {
    vi.spyOn(courseAdminApi, 'get').mockResolvedValue(draftCourse({ version: 3 }))
    const update = vi
      .spyOn(courseAdminApi, 'update')
      .mockResolvedValue(draftCourse({ title: 'Labots kurss', version: 4 }))
    const user = userEvent.setup()
    renderAt('/admin/kursi/7', admin)

    const title = await screen.findByLabelText('Nosaukums')
    await user.clear(title)
    await user.type(title, 'Labots kurss')
    await user.click(screen.getByRole('button', { name: 'Saglabāt' }))

    expect(update).toHaveBeenCalledWith(7, expect.objectContaining({ title: 'Labots kurss', version: 3, priceCents: 4990 }))
    expect(await screen.findByRole('heading', { name: 'Labots kurss' })).toBeInTheDocument()
  })

  it('moves a lesson down by sending the new order', async () => {
    const course = draftCourse({
      sections: [
        {
          id: 1,
          title: 'Ievads',
          position: 0,
          lessons: [
            { id: 10, title: 'Pirmā', type: 'AUDIO', contentText: null, audioRef: null, durationSeconds: null, preview: false, position: 0 },
            { id: 11, title: 'Otrā', type: 'TEXT', contentText: null, audioRef: null, durationSeconds: null, preview: false, position: 1 },
          ],
        },
      ],
    })
    vi.spyOn(courseAdminApi, 'get').mockResolvedValue(course)
    const reorder = vi.spyOn(courseAdminApi, 'reorderLessons').mockResolvedValue(course)
    const user = userEvent.setup()
    renderAt('/admin/kursi/7', admin)

    const [firstDown] = await screen.findAllByRole('button', { name: 'Pārvietot nodarbību uz leju' })
    await user.click(firstDown)

    expect(reorder).toHaveBeenCalledWith(7, 1, [11, 10])
  })
})
