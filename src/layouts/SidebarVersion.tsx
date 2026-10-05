import { useId, useState } from 'react'
import { ArrowUpRight, FileText, Package, X } from 'lucide-react'
import { IconButton } from '@ui/index'
import { version } from '../../package.json'
import styles from './SidebarVersion.module.less'

export default function SidebarVersion({ collapsed }: { collapsed: boolean }) {
  const panelId = useId()
  const titleId = useId()
  const [open, setOpen] = useState(false)

  return <>
    <button
      type="button"
      className={styles.trigger}
      aria-label="版本与更新"
      aria-haspopup="dialog"
      aria-expanded={open}
      aria-controls={panelId}
      popoverTarget={panelId}
      title={collapsed ? `版本与更新 · v${version}` : undefined}
    >
      <Package size={20} strokeWidth={1.5} aria-hidden="true" />
      {!collapsed && <span className={styles.version}>v{version}</span>}
    </button>
    <section
      id={panelId}
      popover="auto"
      role="dialog"
      aria-labelledby={titleId}
      className={[styles.panel, collapsed && styles.panelCollapsed].filter(Boolean).join(' ')}
      onToggle={(event) => {
        const nextOpen = event.newState === 'open'
        setOpen(nextOpen)
        if (nextOpen) event.currentTarget.querySelector('button')?.focus()
      }}
    >
      <header className={styles.header}>
        <h2 id={titleId}>版本与更新</h2>
        <IconButton label="关闭版本面板" popoverTarget={panelId} popoverTargetAction="hide"><X size={15} aria-hidden="true" /></IconButton>
      </header>
      <div className={styles.summary}>
        <span className={styles.packageIcon}><Package size={23} strokeWidth={1.5} aria-hidden="true" /></span>
        <strong>v{version}</strong>
        <span>当前版本 · Atlas Console</span>
      </div>
      <a className={styles.releaseLink} href="https://github.com/wwxst/atlas-console/releases" target="_blank" rel="noopener noreferrer">
        <FileText size={16} aria-hidden="true" />
        <span>查看发布与更新日志</span>
        <ArrowUpRight size={15} aria-hidden="true" />
      </a>
    </section>
  </>
}
