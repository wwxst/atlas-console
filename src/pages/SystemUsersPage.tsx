import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Eye, RotateCcw, Search, X } from 'lucide-react'
import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getSysUsers } from '@/features/systemUsers/api'
import type { SysUser, SysUserStatus } from '@/features/systemUsers/api'
import { AppButton, IconButton, Panel, StatusBadge } from '@ui/index'
import styles from './SystemUsersPage.module.less'

const PAGE_SIZE = 10

function parsePage(value: string | null): number {
  const page = Number(value ?? '1')
  return Number.isInteger(page) && page > 0 ? page : 1
}

function parseStatus(value: string | null): SysUserStatus | undefined {
  if (value === '0') return 0
  if (value === '1') return 1
  return undefined
}

function getInitials(user: SysUser): string {
  const label = user.nickname.trim() || user.username.trim()
  return Array.from(label).slice(-2).join('').toUpperCase()
}

function formatDateTime(value: string): string {
  const [date, time = ''] = value.split('T')
  return time ? `${date} ${time.slice(0, 5)}` : date
}

export default function SystemUsersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const detailsDialogRef = useRef<HTMLDialogElement>(null)
  const [selectedUser, setSelectedUser] = useState<SysUser | null>(null)
  const keyword = searchParams.get('keyword')?.trim() ?? ''
  const status = parseStatus(searchParams.get('status'))
  const page = parsePage(searchParams.get('page'))
  const usersQuery = useQuery({
    queryKey: ['system-users', { page, pageSize: PAGE_SIZE, keyword, status }],
    queryFn: () => getSysUsers({
      page,
      pageSize: PAGE_SIZE,
      keyword: keyword || undefined,
      status,
    }),
    placeholderData: keepPreviousData,
  })

  const updateParams = (updates: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(updates).forEach(([key, value]) => {
      if (value === undefined || value === '') next.delete(key)
      else next.set(key, value)
    })
    setSearchParams(next, { replace: true })
  }

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)
    const nextKeyword = String(formData.get('keyword') ?? '').trim()
    updateParams({ keyword: nextKeyword || undefined, page: undefined })
  }

  const resetFilters = () => {
    setSearchParams(new URLSearchParams(), { replace: true })
  }

  const changePage = (nextPage: number) => {
    updateParams({ page: nextPage <= 1 ? undefined : String(nextPage) })
  }

  const openDetails = (user: SysUser) => {
    setSelectedUser(user)
    requestAnimationFrame(() => detailsDialogRef.current?.showModal())
  }

  const result = usersQuery.data
  const pageCount = Math.max(1, Math.ceil((result?.total ?? 0) / PAGE_SIZE))
  const records = result?.records ?? []

  return <div className={styles.page}>
    <div className={styles.pageHeader}>
      <div><h1>系统用户</h1><p>查询管理后台登录账号及当前状态</p></div>
    </div>

    <Panel className={styles.listPanel}>
      <div className={styles.toolbar}>
        <form className={styles.searchForm} onSubmit={submitSearch} role="search">
          <label className={styles.searchField}>
            <Search size={16} aria-hidden="true" />
            <input key={keyword} name="keyword" defaultValue={keyword} maxLength={30} placeholder="搜索登录账号或昵称" aria-label="搜索系统用户" />
          </label>
          <AppButton type="submit" variant="primary" icon={<Search size={15} />}>查询</AppButton>
        </form>
        <label className={styles.selectField}>
          <span>状态</span>
          <select value={status === undefined ? 'all' : String(status)} onChange={(event) => updateParams({ status: event.target.value === 'all' ? undefined : event.target.value, page: undefined })}>
            <option value="all">全部状态</option>
            <option value="1">正常</option>
            <option value="0">已停用</option>
          </select>
        </label>
        <AppButton variant="ghost" icon={<RotateCcw size={15} />} onClick={resetFilters}>重置</AppButton>
        <span className={styles.resultCount}>{usersQuery.isFetching && result ? '正在更新...' : `共 ${result?.total ?? 0} 个系统用户`}</span>
      </div>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead><tr><th>系统用户</th><th>登录账号</th><th>状态</th><th>创建时间</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>
            {usersQuery.isPending && <tr><td colSpan={6}><div className={styles.state}>正在加载系统用户...</div></td></tr>}
            {usersQuery.isError && <tr><td colSpan={6}><div className={styles.state}><p role="alert">系统用户加载失败，请重试。</p><AppButton icon={<RotateCcw size={15} />} onClick={() => void usersQuery.refetch()}>重试</AppButton></div></td></tr>}
            {usersQuery.isSuccess && records.length === 0 && <tr><td colSpan={6}><div className={styles.state}>没有符合当前条件的系统用户</div></td></tr>}
            {records.map((user) => <tr key={user.id}>
              <td><div className={styles.userCell}><span className={styles.avatar}>{getInitials(user)}</span><span><strong>{user.nickname}</strong><small>系统用户 ID：{user.id}</small></span></div></td>
              <td><span className={styles.username}>{user.username}</span></td>
              <td><StatusBadge tone={user.status === 1 ? 'success' : 'danger'}>{user.status === 1 ? '正常' : '已停用'}</StatusBadge></td>
              <td>{formatDateTime(user.createdAt)}</td>
              <td>{formatDateTime(user.updatedAt)}</td>
              <td><button type="button" className={styles.viewButton} onClick={() => openDetails(user)}><Eye size={15} />查看</button></td>
            </tr>)}
          </tbody>
        </table>
      </div>

      <div className={styles.tableFooter}>
        <span>第 {result?.page ?? page} 页，共 {pageCount} 页</span>
        <div className={styles.pagination}>
          <IconButton label="上一页" disabled={page <= 1 || usersQuery.isPending} onClick={() => changePage(page - 1)}><ChevronLeft size={17} /></IconButton>
          <span>{result?.page ?? page}</span>
          <IconButton label="下一页" disabled={page >= pageCount || usersQuery.isPending} onClick={() => changePage(page + 1)}><ChevronRight size={17} /></IconButton>
        </div>
      </div>
    </Panel>

    <dialog ref={detailsDialogRef} className={styles.dialog} onClose={() => setSelectedUser(null)}>
      {selectedUser && <>
        <div className={styles.dialogHeader}><div><h2>系统用户详情</h2><p>系统用户 ID：{selectedUser.id}</p></div><IconButton label="关闭" onClick={() => detailsDialogRef.current?.close()}><X size={18} /></IconButton></div>
        <div className={styles.detailsIdentity}><span className={styles.detailsAvatar}>{getInitials(selectedUser)}</span><div><strong>{selectedUser.nickname}</strong><span>{selectedUser.username}</span></div><StatusBadge tone={selectedUser.status === 1 ? 'success' : 'danger'}>{selectedUser.status === 1 ? '正常' : '已停用'}</StatusBadge></div>
        <dl className={styles.detailsGrid}>
          <div><dt>登录账号</dt><dd>{selectedUser.username}</dd></div>
          <div><dt>系统用户昵称</dt><dd>{selectedUser.nickname}</dd></div>
          <div><dt>创建时间</dt><dd>{formatDateTime(selectedUser.createdAt)}</dd></div>
          <div><dt>更新时间</dt><dd>{formatDateTime(selectedUser.updatedAt)}</dd></div>
        </dl>
      </>}
    </dialog>
  </div>
}
