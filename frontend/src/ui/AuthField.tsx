import type { HTMLAttributes, ReactNode } from 'react'
import styles from './AuthField.module.less'

export interface AuthFieldProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
  startAdornment?: ReactNode
  endAdornment?: ReactNode
  invalid?: boolean
}

export function AuthField({ children, startAdornment, endAdornment, invalid = false, className, ...props }: AuthFieldProps) {
  return <div {...props} className={[styles.field, invalid && styles.invalid, className].filter(Boolean).join(' ')}>
    {startAdornment && <span className={styles.startAdornment}>{startAdornment}</span>}
    {children}
    {endAdornment && <span className={styles.endAdornment}>{endAdornment}</span>}
  </div>
}
