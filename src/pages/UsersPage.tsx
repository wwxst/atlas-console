import { useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Eye, Plus, RotateCcw, Search, X } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { createUser, getUsers } from '@/features/users/api'
import type { ConsoleUser, UserRole } from '@/features/users/api'
import { AppButton, IconButton, Panel, StatusBadge } from '@ui/index'
import styles from './UsersPage.module.less'

const PAGE_SIZE = 7
const USER_ROLES: UserRole[] = ['超级管理员', '管理员', '运营人员', '财务人员', '审计员']
const DEPARTMENTS = ['运营中心', '产品研发', '市场中心', '财务中心', '客户成功', '风控合规']

export default function UsersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const usersQuery = useQuery({ queryKey: ['users'], queryFn: getUsers })
  const createDialogRef = useRef<HTMLDialogElement>(null)
  const createFormRef = useRef<HTMLFormElement>(null)
  const detailsDialogRef = useRef<HTMLDialogElement>(null)
  const [selectedUser, setSelectedUser] = useState<ConsoleUser | null>(null)

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      createDialogRef.current?.close()
      createFormRef.current?.reset()
    },
  })

  const keyword = searchParams.get('keyword') ?? ''
  const role = searchParams.get('role') ?? 'all'
  const status = searchParams.get('status') ?? 'all'
  const requestedPage = Number(searchParams.get('page') ?? '1')

  const filteredUsers = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase()
    return (usersQuery.data ?? []).filter((user) => {
      const matchesKeyword = !normalizedKeyword || [user.name, user.email, user.phone, user.department].some((value) => value.toLowerCase().includes(normalizedKeyword))
      return matchesKeyword && (role === 'all' || user.role === role) && (status === 'all' || user.status === status)
    })
  }, [keyword, role, status, usersQuery.data])

  const pageCount = Math.max(1, Math.ceil(filteredUsers.length / PAGE_SIZE))
  const page = Number.isFinite(requestedPage) ? Math.min(Math.max(requestedPage, 1), pageCount) : 1
  const visibleUsers = filteredUsers.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  const updateFilter = (key: 'keyword' | 'role' | 'status', value: string) => {
    const next = new URLSearchParams(searchParams)
    if (!value || value === 'all') next.delete(key)
    else next.set(key, value)
    next.delete('page')
    setSearchParams(next, { replace: true })
  }

  const changePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams)
    if (nextPage <= 1) next.delete('page')
    else next.set('page', String(nextPage))
    setSearchParams(next, { replace: true })
  }

  const openDetails = (user: ConsoleUser) => {
    setSelectedUser(user)
    requestAnimationFrame(() => detailsDialogRef.current?.showModal())
  }

  const submitUser = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)
    createMutation.mutate({
      name: String(formData.get('name') ?? '').trim(),
      email: String(formData.get('email') ?? '').trim(),
      phone: String(formData.get('phone') ?? '').trim(),
      department: String(formData.get('department') ?? ''),
      role: String(formData.get('role') ?? '') as UserRole,
    })
  }

  if (usersQuery.isLoading) return <div className={styles.loading}>正在加载用户列表...</div>
  if (usersQuery.isError || !usersQuery.data) return <div className={styles.loading}>用户数据加载失败，请稍后重试。</div>

  return <div className={styles.page}>
    <div className={styles.pageHeader}>
      <div><h1>用户列表</h1><p>管理平台用户、角色和账号状态</p></div>
      <AppButton variant="primary" icon={<Plus size={16} />} onClick={() => createDialogRef.current?.showModal()}>新增用户</AppButton>
    </div>

    <Panel className={styles.listPanel}>
      <div className={styles.toolbar}>
        <label className={styles.searchField}>
          <Search size={16} aria-hidden="true" />
          <input value={keyword} onChange={(event) => updateFilter('keyword', event.target.value)} placeholder="搜索姓名、邮箱、手机或部门" aria-label="搜索用户" />
        </label>
        <label className={styles.selectField}><span>角色</span><select value={role} onChange={(event) => updateFilter('role', event.target.value)}><option value="all">全部角色</option>{USER_ROLES.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
        <label className={styles.selectField}><span>状态</span><select value={status} onChange={(event) => updateFilter('status', event.target.value)}><option value="all">全部状态</option><option value="active">正常</option><option value="disabled">已停用</option></select></label>
        <AppButton variant="ghost" icon={<RotateCcw size={15} />} onClick={() => setSearchParams(new URLSearchParams(), { replace: true })}>重置</AppButton>
        <span className={styles.resultCount}>共 {filteredUsers.length} 个用户</span>
      </div>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead><tr><th>用户</th><th>部门</th><th>角色</th><th>状态</th><th>最近登录</th><th>操作</th></tr></thead>
          <tbody>{visibleUsers.length > 0 ? visibleUsers.map((user) => <tr key={user.id}>
            <td><div className={styles.userCell}><span className={styles.avatar}>{user.initials}</span><span><strong>{user.name}</strong><small>{user.email} · {user.phone}</small></span></div></td>
            <td>{user.department}</td>
            <td><span className={styles.role}>{user.role}</span></td>
            <td><StatusBadge tone={user.status === 'active' ? 'success' : 'danger'}>{user.status === 'active' ? '正常' : '已停用'}</StatusBadge></td>
            <td>{user.lastLogin}</td>
            <td><button type="button" className={styles.viewButton} onClick={() => openDetails(user)}><Eye size={15} />查看</button></td>
          </tr>) : <tr><td colSpan={6}><div className={styles.empty}>没有符合当前条件的用户</div></td></tr>}</tbody>
        </table>
      </div>

      <div className={styles.tableFooter}>
        <span>第 {page} 页，共 {pageCount} 页</span>
        <div className={styles.pagination}><IconButton label="上一页" disabled={page <= 1} onClick={() => changePage(page - 1)}><ChevronLeft size={17} /></IconButton><span>{page}</span><IconButton label="下一页" disabled={page >= pageCount} onClick={() => changePage(page + 1)}><ChevronRight size={17} /></IconButton></div>
      </div>
    </Panel>

    <dialog ref={createDialogRef} className={styles.dialog} onClose={() => { createMutation.reset(); createFormRef.current?.reset() }}>
      <div className={styles.dialogHeader}><div><h2>新增用户</h2><p>创建平台账号并分配初始角色</p></div><IconButton label="关闭" onClick={() => createDialogRef.current?.close()}><X size={18} /></IconButton></div>
      <form ref={createFormRef} className={styles.userForm} onSubmit={submitUser}>
        <div className={styles.formGrid}>
          <label><span>姓名</span><input name="name" required autoComplete="name" placeholder="请输入姓名" /></label>
          <label><span>邮箱</span><input name="email" required type="email" autoComplete="email" placeholder="name@atlas.cn" /></label>
          <label><span>手机号</span><input name="phone" required autoComplete="tel" placeholder="请输入手机号" /></label>
          <label><span>部门</span><select name="department" required defaultValue=""><option value="" disabled>请选择部门</option>{DEPARTMENTS.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
          <label><span>角色</span><select name="role" required defaultValue=""><option value="" disabled>请选择角色</option>{USER_ROLES.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
        </div>
        {createMutation.isError && <p className={styles.formError}>创建失败，请检查信息后重试。</p>}
        <div className={styles.dialogActions}><AppButton type="button" onClick={() => createDialogRef.current?.close()}>取消</AppButton><AppButton type="submit" variant="primary" disabled={createMutation.isPending}>{createMutation.isPending ? '创建中...' : '创建用户'}</AppButton></div>
      </form>
    </dialog>

    <dialog ref={detailsDialogRef} className={[styles.dialog, styles.detailsDialog].join(' ')} onClose={() => setSelectedUser(null)}>
      {selectedUser && <><div className={styles.dialogHeader}><div><h2>用户详情</h2><p>{selectedUser.id}</p></div><IconButton label="关闭" onClick={() => detailsDialogRef.current?.close()}><X size={18} /></IconButton></div><div className={styles.detailsIdentity}><span className={styles.detailsAvatar}>{selectedUser.initials}</span><div><strong>{selectedUser.name}</strong><span>{selectedUser.email}</span></div><StatusBadge tone={selectedUser.status === 'active' ? 'success' : 'danger'}>{selectedUser.status === 'active' ? '正常' : '已停用'}</StatusBadge></div><dl className={styles.detailsGrid}><div><dt>手机号</dt><dd>{selectedUser.phone}</dd></div><div><dt>部门</dt><dd>{selectedUser.department}</dd></div><div><dt>角色</dt><dd>{selectedUser.role}</dd></div><div><dt>最近登录</dt><dd>{selectedUser.lastLogin}</dd></div><div><dt>创建日期</dt><dd>{selectedUser.createdAt}</dd></div></dl></>}
    </dialog>
  </div>
}
