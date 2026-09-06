import { Navigate, Route, Routes } from 'react-router-dom'
import DashboardPage from '@/pages/DashboardPage'
import PlaceholderPage from '@/pages/PlaceholderPage'
import UsersPage from '@/pages/UsersPage'

export default function AppRouter() {
  return <Routes>
    <Route path="/" element={<DashboardPage />} />
    <Route path="/users" element={<UsersPage />} />
    <Route path="/team" element={<Navigate to="/users" replace />} />
    <Route path="/profile" element={<PlaceholderPage title="个人信息" description="个人资料和账号安全设置将在这里展开。" />} />
    <Route path="/settings" element={<PlaceholderPage title="系统设置" description="主题、通知和组织配置将在这里展开。" />} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
}
