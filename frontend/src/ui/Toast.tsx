import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react'
import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import styles from './Toast.module.less'

type ToastTone = 'success' | 'info' | 'warning' | 'danger'
type ToastPlacement = 'top-center' | 'bottom-center'

export interface ToastProps {
  open: boolean
  children: ReactNode
  tone?: ToastTone
  placement?: ToastPlacement
  className?: string
}

const toneIcons = {
  success: CheckCircle2,
  info: Info,
  warning: TriangleAlert,
  danger: AlertCircle,
} as const

export function Toast({ open, children, tone = 'success', placement = 'top-center', className }: ToastProps) {
  const toastRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const toast = toastRef.current
    if (open && toast && !toast.matches(':popover-open')) toast.showPopover()
    if (!open && toast?.matches(':popover-open')) toast.hidePopover()
  }, [open])
  if (!open) return null

  const Icon = toneIcons[tone]

  return <div
    ref={toastRef}
    popover="manual"
    className={[styles.toast, styles[`toast_${tone}`], styles[`toast_${placement.replace('-', '_')}`], className].filter(Boolean).join(' ')}
    role={tone === 'danger' ? 'alert' : 'status'}
    aria-live={tone === 'danger' ? 'assertive' : 'polite'}
  >
    <Icon className={styles.icon} size={17} strokeWidth={2} aria-hidden="true" />
    <span>{children}</span>
  </div>
}
