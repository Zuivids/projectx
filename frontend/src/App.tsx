import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import { RedirectIfLoggedIn, RequireAdmin, RequireAuth } from './auth/guards'
import { Layout } from './components/Layout'
import { AdminCourseEditorPage } from './pages/admin/AdminCourseEditorPage'
import { AdminCoursesPage } from './pages/admin/AdminCoursesPage'
import { AdminPage } from './pages/AdminPage'
import { CatalogPage } from './pages/CatalogPage'
import { CoursePage } from './pages/CoursePage'
import { HomePage } from './pages/HomePage'
import { LandingPage } from './pages/LandingPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PreviewLessonPage } from './pages/PreviewLessonPage'
import { RegisterPage } from './pages/RegisterPage'
import { SecurityPage } from './pages/SecurityPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<LandingPage />} />
        <Route path="kursi" element={<CatalogPage />} />
        <Route path="kursi/:slug" element={<CoursePage />} />
        <Route path="kursi/:slug/nodarbibas/:lessonId" element={<PreviewLessonPage />} />

        <Route element={<RedirectIfLoggedIn />}>
          <Route path="pieslegties" element={<LoginPage />} />
          <Route path="registreties" element={<RegisterPage />} />
        </Route>

        <Route element={<RequireAuth />}>
          <Route path="sakums" element={<HomePage />} />
          <Route path="konts/drosiba" element={<SecurityPage />} />
        </Route>

        <Route element={<RequireAdmin />}>
          <Route path="admin" element={<AdminPage />} />
          <Route path="admin/kursi" element={<AdminCoursesPage />} />
          <Route path="admin/kursi/:courseId" element={<AdminCourseEditorPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}
