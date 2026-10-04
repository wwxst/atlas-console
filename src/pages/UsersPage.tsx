import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Eye, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getUsers } from '@/features/users/api'
import type { User, UserStatus } from '@/features/users/api'
import { AppButton, Avatar, DataTable, Drawer, EmptyState, ListFilters, PaginatedListPanel, SortableDateHeader, StatusBadge, StatusIndicator } from '@ui/index'
import styles from './UsersPage.module.less'

const DEFAULT_PAGE_SIZE = 20
const PAGE_SIZE_OPTIONS = [10, 20, 50]

function parsePage(value: string | null): number {
  const page = Number(value ?? '1')
  return Number.isInteger(page) && page > 0 ? page : 1
}

function parsePageSize(value: string | null): number {
  const pageSize = Number(value ?? DEFAULT_PAGE_SIZE)
  return PAGE_SIZE_OPTIONS.includes(pageSize) ? pageSize : DEFAULT_PAGE_SIZE
}

function parseStatus(value: string | null): UserStatus | undefined {
  if (value === '0') return 0
  if (value === '1') return 1
  return undefined
}

function getDisplayName(user: User): string {
  return user.nickname || user.phone || user.email || `用户 ${user.id}`
}

function getContactInfo(user: User): string {
  if (user.phone && user.email) return `${user.phone} · ${user.email}`
  return user.phone || user.email || '未绑定联系方式'
}

function getInitials(user: User): string {
  return Array.from(getDisplayName(user)).slice(0, 2).join('').toUpperCase()
}

function formatDateTime(value: string): string {
  const [date, time = ''] = value.split('T')
  return time ? `${date} ${time.slice(0, 5)}` : date
}

export default function UsersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const keyword = searchParams.get('keyword')?.trim() ?? ''
  const status = parseStatus(searchParams.get('status'))
  const pageSize = parsePageSize(searchParams.get('pageSize'))
  const page = parsePage(searchParams.get('page'))
  const orderParam = searchParams.get('createdAtOrder')
  const createdAtOrder = orderParam === 'asc' ? 'asc' : 'desc'
  const usersQuery = useQuery({
    queryKey: ['users', { page, pageSize, keyword, status, createdAtOrder }],
    queryFn: () => getUsers({
      page,
      pageSize,
      keyword: keyword || undefined,
      status,
      createdAtOrder,
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

  const resetFilters = () => {
    setSearchParams(new URLSearchParams(), { replace: true })
  }

  const changePage = (nextPage: number) => {
    updateParams({ page: nextPage <= 1 ? undefined : String(nextPage) })
  }

  const changePageSize = (nextPageSize: number) => {
    updateParams({ pageSize: nextPageSize === DEFAULT_PAGE_SIZE ? undefined : String(nextPageSize), page: undefined })
  }

  const changeCreatedAtOrder = () => {
    updateParams({ createdAtOrder: createdAtOrder === 'desc' ? 'asc' : 'desc', page: undefined })
  }

  const openDetails = (user: User) => {
    setSelectedUser(user)
  }

  const result = usersQuery.data
  const records = result?.records ?? []

  return <div className={styles.page}>
    <PaginatedListPanel
      page={result?.page ?? page}
      pageSize={pageSize}
      pageSizeOptions={PAGE_SIZE_OPTIONS}
      total={result?.total ?? 0}
      loading={usersQuery.isPending}
      onPageChange={changePage}
      onPageSizeChange={changePageSize}
      toolbar={<ListFilters
        keyword={keyword}
        status={status === undefined ? 'all' : status === 1 ? '1' : '0'}
        searchPlaceholder="搜索手机号、邮箱或昵称"
        searchLabel="搜索用户"
        onSearch={(value) => updateParams({ keyword: value || undefined, page: undefined })}
        onStatusChange={(value) => updateParams({ status: value === 'all' ? undefined : value, page: undefined })}
        onReset={resetFilters}
        summary={usersQuery.isFetching && result ? '正在更新...' : `共 ${result?.total ?? 0} 个用户`}
      />}
    >
      <DataTable className={styles.table}>
        <thead><tr><th>用户</th><th>手机</th><th>邮箱</th>
          <th aria-sort={createdAtOrder === 'asc' ? 'ascending' : createdAtOrder === 'desc' ? 'descending' : 'none'}>
            <SortableDateHeader order={createdAtOrder} onClick={changeCreatedAtOrder} />
          </th>
          <th>状态</th><th>操作</th></tr></thead>
        <tbody>
          {usersQuery.isPending && <tr><td colSpan={6}><div className={styles.state}>正在加载用户列表...</div></td></tr>}
          {usersQuery.isError && <tr><td colSpan={6}><div className={styles.state}><p role="alert">用户列表加载失败，请重试。</p><AppButton icon={<RotateCcw size={15} />} onClick={() => void usersQuery.refetch()}>重试</AppButton></div></td></tr>}
          {usersQuery.isSuccess && records.length === 0 && <tr><td colSpan={6}><EmptyState description="没有符合当前条件的用户" /></td></tr>}
          {records.map((user) => <tr key={user.id}>
            <td><div className={styles.userCell}><Avatar size={40} src={user.avatar}>{getInitials(user)}</Avatar><span><strong>{getDisplayName(user)}</strong></span></div></td>
            <td><span className={styles.contact}>{user.phone || '未绑定'}</span></td>
            <td><span className={styles.contact}>{user.email || '未绑定'}</span></td>
            <td>{formatDateTime(user.createdAt)}</td>
            <td><StatusIndicator tone={user.status === 1 ? 'success' : 'danger'}>{user.status === 1 ? '正常' : '已停用'}</StatusIndicator></td>
            <td><button type="button" className={styles.viewButton} onClick={() => openDetails(user)}><Eye size={15} />查看</button></td>
          </tr>)}
        </tbody>
      </DataTable>
    </PaginatedListPanel>

    <Drawer open={selectedUser !== null} title="用户详情" subtitle={selectedUser && `用户 ID：${selectedUser.id}`} onClose={() => setSelectedUser(null)}>
      {selectedUser && <>
        <div className={styles.detailsIdentity}><Avatar size={40} src={selectedUser.avatar}>{getInitials(selectedUser)}</Avatar><div><strong>{getDisplayName(selectedUser)}</strong><span>{getContactInfo(selectedUser)}</span></div><StatusBadge tone={selectedUser.status === 1 ? 'success' : 'danger'}>{selectedUser.status === 1 ? '正常' : '已停用'}</StatusBadge></div>
        <dl className={styles.detailsGrid}>
          <div><dt>用户昵称</dt><dd>{selectedUser.nickname || '未设置'}</dd></div>
          <div><dt>手机号</dt><dd>{selectedUser.phone || '未绑定'}</dd></div>
          <div><dt>邮箱</dt><dd>{selectedUser.email || '未绑定'}</dd></div>
          <div><dt>头像</dt><dd>{selectedUser.avatar ? '已设置' : '未设置'}</dd></div>
          <div><dt>创建时间</dt><dd>{formatDateTime(selectedUser.createdAt)}</dd></div>
        </dl>
      </>}
    </Drawer>
  </div>
}
