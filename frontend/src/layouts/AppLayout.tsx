import { useState } from 'react'
import type { ReactNode } from 'react'
import { ChevronDown, ChevronLeft, ChevronRight, HardDrive, LayoutDashboard, Settings, ShieldCheck, Users, UserCog } from 'lucide-react'
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
]

const settingsItems = [
  { key: '/settings', label: '认证配置', icon: <ShieldCheck size={17} /> },
  { key: '/settings/storage', label: '存储配置', icon: <HardDrive size={17} /> },
]

export default function AppLayout({ children }: { children?: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { sidebarCollapsed, toggleSidebar } = useAppStore()
  const [settingsExpanded, setSettingsExpanded] = useState(true)
  const inSettings = location.pathname === '/settings' || location.pathname.startsWith('/settings/')

  return <div className={styles.shell}>
    <TopBar />
    <aside className={[styles.sider, sidebarCollapsed ? styles.siderCollapsed : ''].join(' ')}>
      <nav className={styles.menu} aria-label="主导航">
        {menuItems.map((item) => <button type="button" key={item.key} className={[styles.menuItem, location.pathname === item.key ? styles.menuItemActive : ''].join(' ')} onClick={() => navigate(item.key)} aria-label={item.label} aria-current={location.pathname === item.key ? 'page' : undefined} title={sidebarCollapsed ? item.label : undefined}><span className={styles.menuIcon}>{item.icon}</span><span className={styles.menuLabel}>{item.label}</span></button>)}
        <button type="button" className={[styles.menuItem, inSettings ? styles.menuGroupActive : ''].join(' ')} aria-label="系统设置" aria-expanded={settingsExpanded} aria-controls="settings-navigation" title={sidebarCollapsed ? '系统设置' : undefined} onClick={() => setSettingsExpanded((expanded) => !expanded)}>
          <span className={styles.menuIcon}><Settings size={17} /></span><span className={styles.menuLabel}>系统设置</span>
          <ChevronDown size={15} className={[styles.menuChevron, settingsExpanded ? styles.menuChevronExpanded : ''].join(' ')} />
        </button>
        <div id="settings-navigation" className={styles.submenu} hidden={!settingsExpanded}>
          {settingsItems.map((item) => <button type="button" key={item.key} className={[styles.menuItem, styles.submenuItem, location.pathname === item.key ? styles.menuItemActive : ''].join(' ')} onClick={() => navigate(item.key)} aria-label={item.label} aria-current={location.pathname === item.key ? 'page' : undefined} title={sidebarCollapsed ? item.label : undefined}><span className={styles.menuIcon}>{item.icon}</span><span className={styles.menuLabel}>{item.label}</span></button>)}
        </div>
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
