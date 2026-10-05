import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import { IconButton } from './index'
import styles from './Drawer.module.less'

interface DrawerProps {
  open: boolean
  title: string
  subtitle?: ReactNode
  size?: 'default' | 'wide'
  children: ReactNode
  onClose: () => void
}

export function Drawer({ open, title, subtitle, size = 'default', children, onClose }: DrawerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    if (open && !dialog?.open) dialog?.showModal()
    else if (!open && dialog?.open) dialog.close()
  }, [open])

  return <dialog ref={dialogRef} className={[styles.drawer, size === 'wide' ? styles.wide : ''].join(' ')} aria-labelledby={titleId} onClose={onClose}>
    <header className={styles.header}>
      <div><h2 id={titleId}>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>
      <IconButton label="关闭" onClick={() => dialogRef.current?.close()}><X size={18} /></IconButton>
    </header>
    <div className={styles.body}>{children}</div>
  </dialog>
}
