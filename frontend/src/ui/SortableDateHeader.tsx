import { ChevronDown, ChevronUp } from 'lucide-react'
import type { ButtonHTMLAttributes } from 'react'
import styles from './SortableDateHeader.module.less'

export type SortOrder = 'asc' | 'desc' | undefined

interface SortableDateHeaderProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  order: SortOrder
  label?: string
}

export function SortableDateHeader({ order, label = '创建时间', className, ...props }: SortableDateHeaderProps) {
  const nextLabel = order === undefined ? `按${label}升序` : order === 'asc' ? `按${label}降序` : `取消${label}排序`

  return <button {...props} type="button" className={[styles.sortHeader, className].filter(Boolean).join(' ')} title={nextLabel} aria-label={`${label}，${nextLabel}`}>
    {label}
    <span className={styles.sortArrows} aria-hidden="true">
      <ChevronUp size={15} strokeWidth={2.25} data-active={order === 'asc'} />
      <ChevronDown size={15} strokeWidth={2.25} data-active={order === 'desc'} />
    </span>
  </button>
}
