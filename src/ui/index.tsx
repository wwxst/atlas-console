import { forwardRef, useId } from 'react'
import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode, TableHTMLAttributes } from 'react'
import { ChevronDown, ChevronLeft, ChevronRight, RotateCcw, Search, User } from 'lucide-react'
import styles from './ui.module.less'
export { AuthField } from './AuthField'
export type { AuthFieldProps } from './AuthField'
export { Toast } from './Toast'
export type { ToastProps } from './Toast'
export { RequestErrorToast } from './RequestErrorToast'
export { Drawer } from './Drawer'
export { UserDetailsLayout, UserDetailsSection, UserDetailsField } from './UserDetailsLayout'
export { EmptyState } from './EmptyState'
export { SortableDateHeader } from './SortableDateHeader'
export type { SortOrder } from './SortableDateHeader'

type ButtonVariant = 'primary' | 'secondary' | 'ghost'

export function AppButton({ variant = 'secondary', icon, children, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; icon?: ReactNode }) {
  return <button {...props} className={[styles.button, styles[`button_${variant}`], className].filter(Boolean).join(' ')}>{icon && <span className={styles.buttonIcon}>{icon}</span>}{children}</button>
}

export function FormField({ label, htmlFor, required = false, children }: { label: ReactNode; htmlFor?: string; required?: boolean; children: ReactNode }) {
  return <div className={styles.formField}><label className={styles.formFieldLabel} htmlFor={htmlFor}>{required && <span className={styles.formRequired} aria-hidden="true">*</span>}{label}<span aria-hidden="true">：</span></label><div className={styles.formFieldControl}>{children}</div></div>
}

export function IconButton({ label, children, className, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return <button {...props} aria-label={label} title={label} className={[styles.iconButton, className].filter(Boolean).join(' ')}>{children}</button>
}

export function Avatar({ src, children, size = 32 }: { src?: string; children?: ReactNode; size?: 32 | 40 | 50 }) {
  return <span className={[styles.avatar, size === 40 && styles.avatarLarge, size === 50 && styles.avatarExtraLarge].filter(Boolean).join(' ')} aria-hidden="true">{src ? <img src={src} alt="" /> : children ?? <User size={size >= 40 ? 22 : 18} />}</span>
}

export function DataTable({ children, className, ...props }: TableHTMLAttributes<HTMLTableElement>) {
  return <table {...props} className={[styles.dataTable, className].filter(Boolean).join(' ')}>{children}</table>
}

interface ListFiltersProps {
  keyword: string
  status: 'all' | '0' | '1'
  searchPlaceholder: string
  searchLabel: string
  onSearch: (keyword: string) => void
  onStatusChange: (status: 'all' | '0' | '1') => void
  onReset: () => void
}

export function ListFilters({ keyword, status, searchPlaceholder, searchLabel, onSearch, onStatusChange, onReset }: ListFiltersProps) {
  return <>
    <form className={styles.filterSearchForm} role="search" onSubmit={(event) => {
      event.preventDefault()
      onSearch(String(new FormData(event.currentTarget).get('keyword') ?? '').trim())
    }}>
      <label className={styles.filterSearchField}>
        <Search size={16} aria-hidden="true" />
        <input key={keyword} name="keyword" defaultValue={keyword} maxLength={30} placeholder={searchPlaceholder} aria-label={searchLabel} />
      </label>
      <label className={styles.filterSelectField}>
        <span>状态</span>
        <span className={styles.filterSelectControl}>
          <select name="status" value={status} onChange={(event) => onStatusChange(event.target.value as ListFiltersProps['status'])}>
            <option value="all">全部状态</option>
            <option value="1">正常</option>
            <option value="0">已停用</option>
          </select>
          <ChevronDown className={styles.filterSelectArrow} size={14} strokeWidth={1.5} aria-hidden="true" />
        </span>
      </label>
      <AppButton type="submit" variant="primary" icon={<Search size={15} />}>查询</AppButton>
    </form>
    <AppButton variant="ghost" icon={<RotateCcw size={15} />} onClick={onReset}>重置</AppButton>
  </>
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

interface PaginatedListPanelProps {
  toolbar: ReactNode
  children: ReactNode
  page: number
  pageSize: number
  pageSizeOptions?: number[]
  total: number
  totalUnit?: string
  loading: boolean
  refreshing?: boolean
  onPageChange: (page: number) => void
  onPageSizeChange?: (pageSize: number) => void
}

function getPageItems(page: number, pageCount: number): Array<number | 'ellipsis'> {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_, index) => index + 1)

  const start = Math.max(2, Math.min(page - 1, pageCount - 4))
  const end = Math.min(pageCount - 1, Math.max(page + 1, 5))
  return [1, ...(start > 2 ? ['ellipsis' as const] : []), ...Array.from({ length: end - start + 1 }, (_, index) => start + index), ...(end < pageCount - 1 ? ['ellipsis' as const] : []), pageCount]
}

export function PaginatedListPanel({ toolbar, children, page, pageSize, pageSizeOptions = [10, 20, 50], total, totalUnit = '条', loading, refreshing = false, onPageChange, onPageSizeChange }: PaginatedListPanelProps) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const pageItems = getPageItems(page, pageCount)

  return <Panel>
    <div className={styles.listToolbar}>{toolbar}</div>
    <div className={styles.listTableRegion}>
      <div className={styles.listTableWrap} aria-busy={refreshing} inert={refreshing}>{children}</div>
      {refreshing && <div className={styles.listRefreshOverlay} role="status" aria-label="正在刷新表格"><span className={styles.listRefreshSpinner} aria-hidden="true" /></div>}
    </div>
    <div className={styles.listFooter}>
      <span className={styles.listTotal}>共 {total} {totalUnit}</span>
      <nav className={styles.listPagination} aria-label="列表分页">
        <IconButton label="上一页" disabled={page <= 1 || loading} onClick={() => onPageChange(page - 1)}><ChevronLeft size={16} /></IconButton>
        {pageItems.map((item, index) => item === 'ellipsis'
          ? <span key={`ellipsis-${index}`} className={styles.paginationEllipsis}>...</span>
          : <button key={item} type="button" className={styles.paginationPage} aria-current={item === page ? 'page' : undefined} disabled={loading} onClick={() => onPageChange(item)}>{item}</button>)}
        <IconButton label="下一页" disabled={page >= pageCount || loading} onClick={() => onPageChange(page + 1)}><ChevronRight size={16} /></IconButton>
        <label className={styles.pageSizeSelect}>
          <span className={styles.visuallyHidden}>每页条数</span>
          <select value={pageSize} disabled={loading || !onPageSizeChange} onChange={(event) => onPageSizeChange?.(Number(event.target.value))}>
            {pageSizeOptions.map((option) => <option key={option} value={option}>{option} 条/页</option>)}
          </select>
          <ChevronDown className={styles.pageSizeArrow} size={14} strokeWidth={1.5} aria-hidden="true" />
        </label>
      </nav>
    </div>
  </Panel>
}

type StatusTone = 'success' | 'processing' | 'warning' | 'danger'
export function StatusBadge({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return <span className={[styles.badge, styles[`badge_${tone}`]].join(' ')}>{children}</span>
}

export function StatusIndicator({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return <span className={[styles.statusIndicator, styles[`statusIndicator_${tone}`]].join(' ')}><span className={styles.statusIndicatorDot} aria-hidden="true" />{children}</span>
}

export function ProgressBar({ value, color }: { value: number; color?: string }) {
  return <div className={styles.progressTrack} role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={100}><span className={styles.progressValue} style={{ width: `${value}%`, backgroundColor: color ?? 'var(--atlas-color-brand)' }} /></div>
}
