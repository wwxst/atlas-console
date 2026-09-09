import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from '@/layouts/AppLayout'
import DashboardPage from '@/pages/DashboardPage'
import LoginPage from '@/pages/LoginPage'
import PlaceholderPage from '@/pages/PlaceholderPage'
import SystemUsersPage from '@/pages/SystemUsersPage'
import RequireAdmin from './RequireAdmin'

export default function AppRouter() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<RequireAdmin />}>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="/system-users" element={<SystemUsersPage />} />
        <Route path="/users" element={<Navigate to="/system-users" replace />} />
        <Route path="/team" element={<Navigate to="/system-users" replace />} />
        <Route path="/profile" element={<PlaceholderPage title="个人信息" description="个人资料和账号安全设置将在这里展开。" />} />
        <Route path="/settings" element={<PlaceholderPage title="系统设置" description="主题、通知和组织配置将在这里展开。" />} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
}
