import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, Power, RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getUsers, updateUserStatus } from '@/features/users/api'
import type { User, UserStatus } from '@/features/users/api'
import { UserDetailsDrawer } from '@/features/users/UserDetailsDrawer'
import { AppButton, Avatar, DataTable, EmptyState, ListFilters, PaginatedListPanel, SortableDateHeader, StatusIndicator } from '@ui/index'
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
  return user.nickname || user.phone || user.email || `用户 ${user.userNo}`
}

function getInitials(user: User): string {
  return Array.from(getDisplayName(user)).slice(0, 2).join('').toUpperCase()
}

function formatDateTime(value: string): string {
  const [date, time = ''] = value.split('T')
  return time ? `${date} ${time.slice(0, 5)}` : date
}

export default function UsersPage() {
  const queryClient = useQueryClient()
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

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: UserStatus }) => updateUserStatus(id, status),
    onSuccess: async (_, { id, status }) => {
      setSelectedUser((current) => current?.id === id ? { ...current, status } : current)
      await queryClient.invalidateQueries({ queryKey: ['users'] })
    },
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
      totalUnit="个用户"
      loading={usersQuery.isPending}
      refreshing={usersQuery.isFetching && !usersQuery.isPending && !statusMutation.isPending}
      onPageChange={changePage}
      onPageSizeChange={changePageSize}
      toolbar={<ListFilters
        keyword={keyword}
        status={status === undefined ? 'all' : status === 1 ? '1' : '0'}
        searchPlaceholder="搜索用户ID、手机号、邮箱或昵称"
        searchLabel="搜索用户"
        onSearch={(value) => updateParams({ keyword: value || undefined, page: undefined })}
        onStatusChange={(value) => updateParams({ status: value === 'all' ? undefined : value, page: undefined })}
        onReset={resetFilters}
      />}
    >
      <DataTable className={styles.table}>
        <thead><tr><th>用户ID</th><th>用户</th><th>手机</th><th>邮箱</th>
          <th aria-sort={createdAtOrder === 'asc' ? 'ascending' : createdAtOrder === 'desc' ? 'descending' : 'none'}>
            <SortableDateHeader order={createdAtOrder} onClick={changeCreatedAtOrder} />
          </th>
          <th>状态</th><th>操作</th></tr></thead>
        <tbody>
          {usersQuery.isPending && <tr><td colSpan={7}><div className={styles.state}>正在加载用户列表...</div></td></tr>}
          {usersQuery.isError && <tr><td colSpan={7}><div className={styles.state}><p role="alert">用户列表加载失败，请重试。</p><AppButton icon={<RotateCcw size={15} />} onClick={() => void usersQuery.refetch()}>重试</AppButton></div></td></tr>}
          {usersQuery.isSuccess && records.length === 0 && <tr><td colSpan={7}><EmptyState description="没有符合当前条件的用户" /></td></tr>}
          {records.map((user) => <tr key={user.id}>
            <td className={styles.userId}>{user.userNo}</td>
            <td><div className={styles.userCell}><Avatar size={40} src={user.avatar ?? undefined}>{getInitials(user)}</Avatar><span><strong>{getDisplayName(user)}</strong></span></div></td>
            <td><span className={styles.contact}>{user.phone || '未绑定'}</span></td>
            <td><span className={styles.contact}>{user.email || '未绑定'}</span></td>
            <td>{formatDateTime(user.createdAt)}</td>
            <td><StatusIndicator tone={user.status === 1 ? 'success' : 'danger'}>{user.status === 1 ? '正常' : '已停用'}</StatusIndicator></td>
            <td><div className={styles.rowActions}>
              <button type="button" className={styles.viewButton} onClick={() => openDetails(user)}><Eye size={15} />查看</button>
              <button type="button" className={styles.statusButton} aria-busy={statusMutation.isPending && statusMutation.variables?.id === user.id} disabled={statusMutation.isPending} onClick={() => statusMutation.mutate({ id: user.id, status: user.status === 1 ? 0 : 1 })}>
                <Power size={15} />{user.status === 1 ? '停用' : '启用'}
              </button>
            </div></td>
          </tr>)}
        </tbody>
      </DataTable>
    </PaginatedListPanel>

    {selectedUser && <UserDetailsDrawer key={selectedUser.id} user={selectedUser} onClose={() => setSelectedUser(null)} onSaved={(saved) => {
      setSelectedUser((current) => current?.id === saved.id ? saved : current)
      void queryClient.invalidateQueries({ queryKey: ['users'] })
    }} />}
  </div>
}
