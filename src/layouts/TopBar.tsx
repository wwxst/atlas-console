import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bell, ChevronDown, LogOut, Moon, Settings, Sun, User } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { getNotifications } from '@/features/notifications/api'
import { getCurrentUser } from '@/features/session/api'
import { IconButton, SearchInput } from '@ui/index'
import { useAppStore } from '@/stores/appStore'
import styles from './TopBar.module.less'

type OpenPanel = 'notifications' | 'account' | null

export default function TopBar() {
  const navigate = useNavigate()
  const location = useLocation()
  const theme = useAppStore((state) => state.theme)
  const setTheme = useAppStore((state) => state.setTheme)
  const [openPanel, setOpenPanel] = useState<OpenPanel>(null)
  const [searchValue, setSearchValue] = useState(() => new URLSearchParams(location.search).get('q') ?? '')
  const controlsRef = useRef<HTMLDivElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)
  const hoverCloseTimerRef = useRef<number | null>(null)
  const user = useQuery({ queryKey: ['current-user'], queryFn: getCurrentUser })
  const notifications = useQuery({ queryKey: ['notifications'], queryFn: getNotifications })
  const unreadCount = notifications.data?.filter((item) => item.unread).length ?? 0

  useEffect(() => {
    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!controlsRef.current?.contains(event.target as Node)) setOpenPanel(null)
    }
    const handleKeyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpenPanel(null)
      }
      if (event.key.toLowerCase() === 'k' && (event.ctrlKey || event.metaKey)) {
        event.preventDefault()
        searchRef.current?.focus()
      }
    }
    document.addEventListener('pointerdown', closeOnOutsideClick)
    document.addEventListener('keydown', handleKeyboard)
    return () => {
      if (hoverCloseTimerRef.current !== null) window.clearTimeout(hoverCloseTimerRef.current)
      document.removeEventListener('pointerdown', closeOnOutsideClick)
      document.removeEventListener('keydown', handleKeyboard)
    }
  }, [])

  const submitSearch = () => {
    const params = new URLSearchParams(location.search)
    if (searchValue.trim()) params.set('q', searchValue.trim())
    else params.delete('q')
    navigate({ pathname: location.pathname, search: params.toString() }, { replace: true })
  }

  const clearScheduledClose = () => {
    if (hoverCloseTimerRef.current === null) return
    window.clearTimeout(hoverCloseTimerRef.current)
    hoverCloseTimerRef.current = null
  }

  const showPanel = (panel: Exclude<OpenPanel, null>) => {
    clearScheduledClose()
    setOpenPanel(panel)
  }

  const schedulePanelClose = () => {
    clearScheduledClose()
    hoverCloseTimerRef.current = window.setTimeout(() => {
      setOpenPanel(null)
      hoverCloseTimerRef.current = null
    }, 150)
  }

  return <header className={styles.topbar}>
    <button type="button" className={styles.brand} onClick={() => navigate('/')} aria-label="返回工作台">
      <span className={styles.brandMark}>A</span>
      <span className={styles.brandText}><strong>Atlas Console</strong><small>运营管理平台</small></span>
    </button>

    <div className={styles.actions} ref={controlsRef}>
      <div className={styles.desktopSearch}><SearchInput ref={searchRef} value={searchValue} onValueChange={setSearchValue} onSubmit={submitSearch} placeholder="搜索菜单、成员或项目" label="全局搜索" /></div>
      <IconButton label={theme === 'light' ? '切换深色主题' : '切换浅色主题'} onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}>{theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}</IconButton>

      <div className={styles.control} onMouseEnter={() => showPanel('notifications')} onMouseLeave={schedulePanelClose}>
        <IconButton label={`通知${unreadCount ? `，${unreadCount} 条未读` : ''}`} aria-expanded={openPanel === 'notifications'} onClick={() => showPanel('notifications')}><Bell size={18} />{unreadCount > 0 && <span className={styles.notificationDot} />}</IconButton>
        {openPanel === 'notifications' && <div className={[styles.popover, styles.notificationPanel].join(' ')} role="dialog" aria-label="通知">
          <div className={styles.popoverHeader}><strong>通知</strong><span>{unreadCount} 条未读</span></div>
          <div className={styles.notificationList}>{notifications.data?.map((item) => <button type="button" key={item.id} className={styles.notificationItem}><span className={item.unread ? styles.unread : ''} /><span><strong>{item.title}</strong><small>{item.time}</small></span></button>)}</div>
          <button type="button" className={styles.popoverFooter}>查看全部通知</button>
        </div>}
      </div>

      <div className={styles.control} onMouseEnter={() => showPanel('account')} onMouseLeave={schedulePanelClose}>
        <button type="button" className={styles.accountButton} aria-expanded={openPanel === 'account'} onClick={() => showPanel('account')}>
          <span className={styles.avatar}>{user.data?.initials ?? '用'}</span>
          <span className={styles.accountText}><strong>{user.data?.name ?? '用户'}</strong><small>{user.data?.role ?? '加载中'}</small></span>
          <ChevronDown size={14} />
        </button>
        {openPanel === 'account' && <div className={[styles.popover, styles.accountPanel].join(' ')} role="menu">
          <button type="button" role="menuitem" onClick={() => { navigate('/profile'); setOpenPanel(null) }}><User size={16} />个人信息</button>
          <button type="button" role="menuitem" onClick={() => { navigate('/settings'); setOpenPanel(null) }}><Settings size={16} />系统设置</button>
          <div className={styles.menuDivider} />
          <button type="button" role="menuitem" className={styles.logout} onClick={() => { localStorage.removeItem('atlas-token'); setOpenPanel(null); navigate('/') }}><LogOut size={16} />退出登录</button>
        </div>}
      </div>
    </div>
  </header>
}
