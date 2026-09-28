import { useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { LogOut, RotateCcw } from 'lucide-react'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { ADMIN_TOKEN_KEY, CURRENT_SYS_USER_QUERY_KEY, getCurrentSysUser } from '@/features/auth/api'
import { AppButton } from '@ui/index'
import styles from './RequireAdmin.module.less'

export default function RequireAdmin() {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const token = localStorage.getItem(ADMIN_TOKEN_KEY)
  const currentUserQuery = useQuery({
    queryKey: CURRENT_SYS_USER_QUERY_KEY,
    queryFn: getCurrentSysUser,
    enabled: Boolean(token),
    retry: false,
  })

  if (!token) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  }

  if (currentUserQuery.isPending) {
    return <main className={styles.page}><div className={styles.state}>正在验证登录状态...</div></main>
  }

  if (currentUserQuery.isError) {
    if (axios.isAxiosError(currentUserQuery.error) && currentUserQuery.error.response?.status === 401) {
      return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
    }

    const signOut = () => {
      localStorage.removeItem(ADMIN_TOKEN_KEY)
      queryClient.clear()
      navigate('/login', { replace: true })
    }

    return <main className={styles.page}>
      <section className={styles.state}>
        <h1>无法进入管理后台</h1>
        <p>登录状态验证失败，请重试。</p>
        <div className={styles.actions}>
          <AppButton icon={<RotateCcw size={16} />} onClick={() => void currentUserQuery.refetch()}>重试</AppButton>
          <AppButton variant="ghost" icon={<LogOut size={16} />} onClick={signOut}>退出登录</AppButton>
        </div>
      </section>
    </main>
  }

  return <Outlet />
}
