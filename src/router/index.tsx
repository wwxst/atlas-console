import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from '@/layouts/AppLayout'
import DashboardPage from '@/pages/DashboardPage'
import LoginPage from '@/pages/LoginPage'
import PlaceholderPage from '@/pages/PlaceholderPage'
import SystemUsersPage from '@/pages/SystemUsersPage'
import UsersPage from '@/pages/UsersPage'
import SettingsPage from '@/pages/SettingsPage'
import StorageSettingsPage from '@/pages/StorageSettingsPage'
import RequireAdmin from './RequireAdmin'

export default function AppRouter() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<RequireAdmin />}>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="/system-users" element={<SystemUsersPage />} />
        <Route path="/team" element={<Navigate to="/system-users" replace />} />
        <Route path="/profile" element={<PlaceholderPage description="个人资料和账号安全设置将在这里展开。" />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/storage" element={<StorageSettingsPage />} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
}
