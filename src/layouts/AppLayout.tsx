import type { ReactNode } from 'react'
import { ChevronLeft, ChevronRight, LayoutDashboard, Settings, Users, UserCog } from 'lucide-react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { IconButton } from '@ui/index'
import { useAppStore } from '@/stores/appStore'
import TopBar from './TopBar'
import SidebarVersion from './SidebarVersion'
import styles from './AppLayout.module.less'

const menuItems = [
  { key: '/', label: '工作台', icon: <LayoutDashboard size={17} /> },
  { key: '/users', label: '普通用户', icon: <Users size={17} /> },
  { key: '/system-users', label: '系统用户', icon: <UserCog size={17} /> },
  { key: '/settings', label: '系统设置', icon: <Settings size={17} /> },
]

export default function AppLayout({ children }: { children?: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { sidebarCollapsed, toggleSidebar } = useAppStore()

  return <div className={styles.shell}>
    <TopBar />
    <aside className={[styles.sider, sidebarCollapsed ? styles.siderCollapsed : ''].join(' ')}>
      <nav className={styles.menu} aria-label="主导航">
        {menuItems.map((item) => <button type="button" key={item.key} className={[styles.menuItem, location.pathname === item.key ? styles.menuItemActive : ''].join(' ')} onClick={() => navigate(item.key)} aria-label={item.label} title={sidebarCollapsed ? item.label : undefined}><span className={styles.menuIcon}>{item.icon}</span><span className={styles.menuLabel}>{item.label}</span></button>)}
      </nav>
      <div className={styles.siderFooter}>
        <SidebarVersion collapsed={sidebarCollapsed} />
        <IconButton className={styles.collapseButton} label={sidebarCollapsed ? '展开导航' : '收起导航'} onClick={toggleSidebar}>{sidebarCollapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}</IconButton>
      </div>
    </aside>
    <div className={[styles.main, sidebarCollapsed ? styles.mainCollapsed : ''].join(' ')}>
      <main className={styles.content}>{children ?? <Outlet />}</main>
    </div>
  </div>
}
