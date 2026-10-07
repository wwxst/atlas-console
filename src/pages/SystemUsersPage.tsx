import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, KeyRound, Pencil, Plus, Power, RotateCcw, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { createSysUser, deleteSysUser, getSysUsers, resetSysUserPassword, updateSysUser, updateSysUserStatus, uploadSysUserAvatar } from '@/features/systemUsers/api'
import type { CreateSysUserInput, SysUser, SysUserStatus, UpdateSysUserInput } from '@/features/systemUsers/api'
import { getApiErrorMessage } from '@/services/api'
import { AppButton, Avatar, DataTable, Drawer, EmptyState, ListFilters, PaginatedListPanel, SortableDateHeader, StatusIndicator, UserDetailsLayout, UserDetailsField, UserDetailsSection } from '@ui/index'
import styles from './SystemUsersPage.module.less'

const DEFAULT_PAGE_SIZE = 20
const PAGE_SIZE_OPTIONS = [10, 20, 50]
const USERNAME_MAX_LENGTH = 20
const NICKNAME_MAX_LENGTH = 30
const PASSWORD_MIN_LENGTH = 8
const PASSWORD_MAX_LENGTH = 24
const AVATAR_MAX_SIZE = 2 * 1024 * 1024
const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp']

function parsePage(value: string | null): number {
  const page = Number(value ?? '1')
  return Number.isInteger(page) && page > 0 ? page : 1
}

function parsePageSize(value: string | null): number {
  const pageSize = Number(value ?? DEFAULT_PAGE_SIZE)
  return PAGE_SIZE_OPTIONS.includes(pageSize) ? pageSize : DEFAULT_PAGE_SIZE
}

function parseStatus(value: string | null): SysUserStatus | undefined {
  if (value === '0') return 0
  if (value === '1') return 1
  return undefined
}

function getInitials(user: SysUser): string {
  const label = user.nickname.trim() || user.username.trim()
  return Array.from(label).slice(0, 2).join('').toUpperCase()
}

function formatDateTime(value: string): string {
  const [date, time = ''] = value.split('T')
  return time ? `${date} ${time.slice(0, 5)}` : date
}

interface SysUserFormValues {
  username: string
  nickname: string
  status?: SysUserStatus
  password?: string
  confirmPassword?: string
  avatarFile?: File
}

function readSysUserForm(form: HTMLFormElement, includeCreateFields: boolean): SysUserFormValues | string {
  const data = new FormData(form)
  const username = String(data.get('username') ?? '').trim()
  const nickname = String(data.get('nickname') ?? '').trim()
  const statusValue = String(data.get('status') ?? '')
  const password = String(data.get('password') ?? '')
  const confirmPassword = String(data.get('confirmPassword') ?? '')

  if (!username) return '请输入登录账号'
  if (username.length > USERNAME_MAX_LENGTH) return `登录账号不能超过 ${USERNAME_MAX_LENGTH} 个字符`
  if (!nickname) return '请输入系统用户昵称'
  if (nickname.length > NICKNAME_MAX_LENGTH) return `系统用户昵称不能超过 ${NICKNAME_MAX_LENGTH} 个字符`
  if (includeCreateFields && statusValue !== '0' && statusValue !== '1') return '请选择有效的账号状态'
  if (includeCreateFields && (!password.trim() || password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH)) {
    return `登录密码需为 ${PASSWORD_MIN_LENGTH} 到 ${PASSWORD_MAX_LENGTH} 个字符`
  }
  if (includeCreateFields && password !== confirmPassword) return '两次输入的密码不一致'
  const avatarEntry = data.get('avatar')
  const avatarFile = avatarEntry instanceof File && avatarEntry.size > 0 ? avatarEntry : undefined
  if (avatarFile && !AVATAR_TYPES.includes(avatarFile.type)) return '头像仅支持 JPG、PNG 或 WEBP 格式'
  if (avatarFile && avatarFile.size > AVATAR_MAX_SIZE) return '头像文件不能超过 2MB'

  return {
    username,
    nickname,
    ...(statusValue === '0' || statusValue === '1' ? { status: Number(statusValue) as SysUserStatus } : {}),
    ...(includeCreateFields ? { password } : {}),
    ...(includeCreateFields ? { confirmPassword } : {}),
    ...(avatarFile ? { avatarFile } : {}),
  }
}

function readPasswordForm(form: HTMLFormElement): { password: string } | { error: string } {
  const password = String(new FormData(form).get('password') ?? '')
  if (!password.trim() || password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH) {
    return { error: `登录密码需为 ${PASSWORD_MIN_LENGTH} 到 ${PASSWORD_MAX_LENGTH} 个字符` }
  }
  return { password }
}

export default function SystemUsersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [selectedUser, setSelectedUser] = useState<SysUser | null>(null)
  const [editor, setEditor] = useState<{ mode: 'create' | 'edit'; user?: SysUser } | null>(null)
  const [passwordUser, setPasswordUser] = useState<SysUser | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null)

  useEffect(() => () => {
    if (avatarPreview?.startsWith('blob:')) URL.revokeObjectURL(avatarPreview)
  }, [avatarPreview])
  const keyword = searchParams.get('keyword')?.trim() ?? ''
  const status = parseStatus(searchParams.get('status'))
  const pageSize = parsePageSize(searchParams.get('pageSize'))
  const page = parsePage(searchParams.get('page'))
  const orderParam = searchParams.get('createdAtOrder')
  const createdAtOrder = orderParam === 'desc' ? 'desc' : 'asc'
  const usersQuery = useQuery({
    queryKey: ['system-users', { page, pageSize, keyword, status, createdAtOrder }],
    queryFn: () => getSysUsers({
      page,
      pageSize,
      keyword: keyword || undefined,
      status,
      createdAtOrder,
    }),
    placeholderData: keepPreviousData,
  })
  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: SysUserStatus }) => updateSysUserStatus(id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['system-users'] }),
  })
  const createMutation = useMutation({
    mutationFn: async ({ input, avatarFile }: { input: CreateSysUserInput; avatarFile?: File }) => {
      const user = await createSysUser(input)
      return avatarFile ? uploadSysUserAvatar(user.id, avatarFile) : user
    },
    onSuccess: () => { setFormError(null); setAvatarPreview(null); setEditor(null); void queryClient.invalidateQueries({ queryKey: ['system-users'] }) },
    onError: (error) => setFormError(getApiErrorMessage(error, '新增系统用户失败，请检查输入后重试。')),
  })
  const updateMutation = useMutation({
    mutationFn: async ({ id, input, avatarFile }: { id: number; input: UpdateSysUserInput; avatarFile?: File }) => {
      const user = await updateSysUser(id, input)
      return avatarFile ? uploadSysUserAvatar(user.id, avatarFile) : user
    },
    onSuccess: (saved) => { setFormError(null); setAvatarPreview(null); setEditor(null); setSelectedUser((current) => current?.id === saved.id ? saved : current); void queryClient.invalidateQueries({ queryKey: ['system-users'] }) },
  })
  const passwordMutation = useMutation({
    mutationFn: ({ id, password }: { id: number; password: string }) => resetSysUserPassword(id, password),
    onSuccess: () => { setFormError(null); setPasswordUser(null) },
    onError: (error) => setFormError(getApiErrorMessage(error, '重置密码失败，请检查输入后重试。')),
  })
  const deleteMutation = useMutation({
    mutationFn: deleteSysUser,
    onSuccess: () => { setSelectedUser(null); void queryClient.invalidateQueries({ queryKey: ['system-users'] }) },
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
    updateParams({ createdAtOrder: createdAtOrder === 'asc' ? 'desc' : undefined, page: undefined })
  }

  const openDetails = (user: SysUser) => {
    setEditor(null)
    setFormError(null)
    setAvatarPreview(null)
    setSelectedUser(user)
  }

  const openEditor = (nextEditor: { mode: 'create' | 'edit'; user?: SysUser }) => {
    setFormError(null)
    setAvatarPreview(null)
    setEditor(nextEditor)
  }

  const closeEditor = () => {
    setFormError(null)
    setAvatarPreview(null)
    setEditor(null)
  }

  const handleAvatarChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    if (!AVATAR_TYPES.includes(file.type)) {
      setFormError('头像仅支持 JPG、PNG 或 WEBP 格式')
      event.target.value = ''
      return
    }
    if (file.size > AVATAR_MAX_SIZE) {
      setFormError('头像文件不能超过 2MB')
      event.target.value = ''
      return
    }
    setFormError(null)
    setAvatarPreview(URL.createObjectURL(file))
  }

  const openPasswordEditor = (user: SysUser) => {
    setFormError(null)
    setPasswordUser(user)
  }

  const closePasswordEditor = () => {
    setFormError(null)
    setPasswordUser(null)
  }

  const submitSysUserForm = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (createMutation.isPending || updateMutation.isPending) return
    const values = readSysUserForm(event.currentTarget, editor?.mode === 'create')
    if (typeof values === 'string') {
      setFormError(values)
      return
    }

    setFormError(null)
    if (editor?.mode === 'create') {
      createMutation.mutate({ input: { username: values.username, nickname: values.nickname, status: values.status!, password: values.password! } })
      return
    }
    if (editor?.user) updateMutation.mutate({ id: editor.user.id, input: { username: values.username, nickname: values.nickname }, avatarFile: values.avatarFile })
  }

  const submitPasswordForm = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!passwordUser) return
    const values = readPasswordForm(event.currentTarget)
    if ('error' in values) {
      setFormError(values.error)
      return
    }
    setFormError(null)
    passwordMutation.mutate({ id: passwordUser.id, password: values.password })
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
      toolbar={<><ListFilters
          keyword={keyword}
          status={status === undefined ? 'all' : status === 1 ? '1' : '0'}
          searchPlaceholder="搜索登录账号或昵称"
          searchLabel="搜索系统用户"
          onSearch={(value) => updateParams({ keyword: value || undefined, page: undefined })}
          onStatusChange={(value) => updateParams({ status: value === 'all' ? undefined : value, page: undefined })}
          onReset={resetFilters}
        /><AppButton variant="primary" icon={<Plus size={15} />} onClick={() => openEditor({ mode: 'create' })}>新增用户</AppButton></>}
    >
      <DataTable className={styles.table}>
        <thead><tr><th>用户</th><th>账号</th>
          <th aria-sort={createdAtOrder === 'asc' ? 'ascending' : createdAtOrder === 'desc' ? 'descending' : 'none'}>
            <SortableDateHeader order={createdAtOrder} onClick={changeCreatedAtOrder} />
          </th>
          <th>状态</th><th>操作</th></tr></thead>
        <tbody>
          {usersQuery.isPending && <tr><td colSpan={5}><div className={styles.state}>正在加载系统用户...</div></td></tr>}
          {usersQuery.isError && <tr><td colSpan={5}><div className={styles.state}><p role="alert">系统用户加载失败，请重试。</p><AppButton icon={<RotateCcw size={15} />} onClick={() => void usersQuery.refetch()}>重试</AppButton></div></td></tr>}
          {usersQuery.isSuccess && records.length === 0 && <tr><td colSpan={5}><EmptyState description="没有符合当前条件的系统用户" /></td></tr>}
          {records.map((user) => <tr key={user.id}>
            <td><div className={styles.userCell}><Avatar size={40} src={user.avatar ?? undefined}>{getInitials(user)}</Avatar><span><strong>{user.nickname}</strong></span></div></td>
            <td><span className={styles.username}>{user.username}</span></td>
            <td>{formatDateTime(user.createdAt)}</td>
            <td><StatusIndicator tone={user.status === 1 ? 'success' : 'danger'}>{user.status === 1 ? '正常' : '已停用'}</StatusIndicator></td>
            <td><div className={styles.rowActions}>
              <button type="button" className={styles.viewButton} onClick={() => openDetails(user)}><Eye size={15} />查看</button>
              <button type="button" className={styles.statusButton} aria-busy={statusMutation.isPending && statusMutation.variables?.id === user.id} disabled={statusMutation.isPending && statusMutation.variables?.id === user.id} onClick={() => statusMutation.mutate({ id: user.id, status: user.status === 1 ? 0 : 1 })}><Power size={15} />{user.status === 1 ? '停用' : '启用'}</button>
              <button type="button" className={styles.deleteButton} disabled={deleteMutation.isPending} onClick={() => { if (window.confirm(`确定删除系统用户“${user.username}”吗？`)) deleteMutation.mutate(user.id) }}><Trash2 size={15} />{deleteMutation.isPending && deleteMutation.variables === user.id ? '删除中...' : '删除'}</button>
            </div></td>
          </tr>)}
        </tbody>
      </DataTable>
    </PaginatedListPanel>

    <Drawer open={selectedUser !== null} title="系统用户详情" size="wide" onClose={() => { setSelectedUser(null); closeEditor() }}>
      {selectedUser && <form onSubmit={submitSysUserForm}>
        <UserDetailsLayout id={selectedUser.id} name={selectedUser.nickname} avatar={avatarPreview ?? selectedUser.avatar} initials={getInitials(selectedUser)} actions={editor?.mode === 'edit' ? <>
          <AppButton type="button" disabled={updateMutation.isPending} onClick={closeEditor}>取消</AppButton>
          <AppButton type="submit" variant="primary" disabled={updateMutation.isPending}>{updateMutation.isPending ? '保存中...' : '保存'}</AppButton>
        </> : <>
          <AppButton type="button" icon={<KeyRound size={14} />} onClick={(event) => { event.preventDefault(); setSelectedUser(null); openPasswordEditor(selectedUser) }}>重置密码</AppButton>
          <AppButton type="button" variant="primary" icon={<Pencil size={14} />} onClick={(event) => { event.preventDefault(); openEditor({ mode: 'edit', user: selectedUser }) }}>编辑</AppButton>
        </>}>
          <UserDetailsSection title="基本信息">
            <UserDetailsField label="用户昵称" htmlFor={editor?.mode === 'edit' ? 'sys-detail-nickname' : undefined} full compact>{editor?.mode === 'edit' ? <input id="sys-detail-nickname" name="nickname" defaultValue={selectedUser.nickname} maxLength={NICKNAME_MAX_LENGTH} required autoFocus disabled={updateMutation.isPending} /> : selectedUser.nickname}</UserDetailsField>
            <UserDetailsField label="登录账号" htmlFor={editor?.mode === 'edit' ? 'sys-detail-username' : undefined}>{editor?.mode === 'edit' ? <input id="sys-detail-username" name="username" defaultValue={selectedUser.username} maxLength={USERNAME_MAX_LENGTH} required disabled={updateMutation.isPending} /> : selectedUser.username}</UserDetailsField>
            {editor?.mode === 'edit' && <UserDetailsField label="头像" full><div className={styles.avatarPicker}><Avatar size={40} src={avatarPreview ?? selectedUser.avatar ?? undefined}>{getInitials(selectedUser)}</Avatar><label className={styles.fileButton}>选择图片<input name="avatar" type="file" accept="image/jpeg,image/png,image/webp" onChange={handleAvatarChange} disabled={updateMutation.isPending} /></label><small>支持 JPG、PNG、WEBP，最大 2MB</small></div></UserDetailsField>}
          </UserDetailsSection>
          <UserDetailsSection title="账号信息"><UserDetailsField label="用户状态">{selectedUser.status === 1 ? '正常' : '已停用'}</UserDetailsField><UserDetailsField label="创建时间">{formatDateTime(selectedUser.createdAt)}</UserDetailsField></UserDetailsSection>
          {formError && editor?.mode === 'edit' && <p className={styles.formError} role="alert">{formError}</p>}
        </UserDetailsLayout>
      </form>}
    </Drawer>

    <Drawer open={editor?.mode === 'create'} title="新增系统用户" centered onClose={closeEditor}>
      {editor?.mode === 'create' && <form className={styles.form} onSubmit={submitSysUserForm}>
        <label>用户昵称<input name="nickname" maxLength={NICKNAME_MAX_LENGTH} placeholder="请输入用户昵称" required /></label>
        <label>登录账号<input name="username" maxLength={USERNAME_MAX_LENGTH} placeholder="请输入登录账号" required /></label>
        <label>登录密码<input name="password" type="password" maxLength={PASSWORD_MAX_LENGTH} minLength={PASSWORD_MIN_LENGTH} placeholder="请输入登录密码" required /></label>
        <label>确认密码<input name="confirmPassword" type="password" maxLength={PASSWORD_MAX_LENGTH} minLength={PASSWORD_MIN_LENGTH} placeholder="请再次输入登录密码" required /></label>
        <fieldset className={styles.statusField}>
          <legend>状态</legend>
          <div className={styles.statusOptions}>
            <label><input type="radio" name="status" value="1" defaultChecked />开启</label>
            <label><input type="radio" name="status" value="0" />关闭</label>
          </div>
        </fieldset>
        {formError && <p className={styles.formError} role="alert">{formError}</p>}
        <div className={styles.formActions}><AppButton type="button" onClick={closeEditor}>取消</AppButton><AppButton type="submit" variant="primary" disabled={createMutation.isPending || updateMutation.isPending}>{createMutation.isPending || updateMutation.isPending ? '保存中...' : '保存'}</AppButton></div>
      </form>}
    </Drawer>

    <Drawer open={passwordUser !== null} title="重置系统用户密码" subtitle={passwordUser?.username} onClose={closePasswordEditor}>
      {passwordUser && <form className={styles.form} onSubmit={submitPasswordForm}><label>新密码<input name="password" type="password" minLength={PASSWORD_MIN_LENGTH} maxLength={PASSWORD_MAX_LENGTH} required autoFocus /></label>{formError && <p className={styles.formError} role="alert">{formError}</p>}<div className={styles.formActions}><AppButton type="button" onClick={closePasswordEditor}>取消</AppButton><AppButton type="submit" variant="primary" disabled={passwordMutation.isPending}>{passwordMutation.isPending ? '提交中...' : '确认重置'}</AppButton></div></form>}
    </Drawer>
  </div>
}
