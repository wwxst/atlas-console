import { forwardRef, useId } from 'react'
import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react'
import { Search } from 'lucide-react'
import styles from './ui.module.less'
export { AuthField } from './AuthField'
export type { AuthFieldProps } from './AuthField'
export { Toast } from './Toast'
export type { ToastProps } from './Toast'

type ButtonVariant = 'primary' | 'secondary' | 'ghost'

export function AppButton({ variant = 'secondary', icon, children, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; icon?: ReactNode }) {
  return <button {...props} className={[styles.button, styles[`button_${variant}`], className].filter(Boolean).join(' ')}>{icon && <span className={styles.buttonIcon}>{icon}</span>}{children}</button>
}

export function IconButton({ label, children, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return <button {...props} aria-label={label} title={label} className={[styles.iconButton, className].filter(Boolean).join(' ')}>{children}</button>
}

interface SearchInputProps {
  value: string
  placeholder?: string
  label?: string
  onValueChange: (value: string) => void
  onSubmit: () => void
  className?: string
}

export const SearchInput = forwardRef<HTMLInputElement, SearchInputProps>(function SearchInput({ value, placeholder = '搜索', label = '搜索', onValueChange, onSubmit, className }, ref) {
  const inputId = useId()
  return <form className={[styles.search, className].filter(Boolean).join(' ')} onSubmit={(event) => { event.preventDefault(); onSubmit() }} role="search">
    <label className={styles.visuallyHidden} htmlFor={inputId}>{label}</label>
    <Search size={16} aria-hidden="true" />
    <input ref={ref} id={inputId} type="search" value={value} placeholder={placeholder} onChange={(event) => onValueChange(event.target.value)} />
    <kbd>Ctrl K</kbd>
  </form>
})

export function Panel({ title, extra, children, className, ...props }: HTMLAttributes<HTMLElement> & { title?: ReactNode; extra?: ReactNode }) {
  return <section {...props} className={[styles.panel, className].filter(Boolean).join(' ')}>{(title || extra) && <div className={styles.panelHeader}>{title && <h2>{title}</h2>}{extra && <div>{extra}</div>}</div>}<div className={styles.panelBody}>{children}</div></section>
}

type StatusTone = 'success' | 'processing' | 'warning' | 'danger'
export function StatusBadge({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return <span className={[styles.badge, styles[`badge_${tone}`]].join(' ')}>{children}</span>
}

export function ProgressBar({ value, color }: { value: number; color?: string }) {
  return <div className={styles.progressTrack} role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100}><span className={styles.progressValue} style={{ width: `${value}%`, backgroundColor: color ?? 'var(--atlas-color-brand)' }} /></div>
}
