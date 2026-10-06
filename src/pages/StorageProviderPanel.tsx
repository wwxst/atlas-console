import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Trash2 } from 'lucide-react'
import { AppButton, DataTable, Drawer, FormField, PaginatedListPanel } from '@ui/index'
import { bindStorageSpace, deleteStorageSpace, getOssCredentials, getStorageSpaces, OSS_CREDENTIALS_QUERY_KEY, setDefaultStorageSpace, STORAGE_SPACES_QUERY_KEY, syncStorageSpaces, updateOssCredentials, updateStorageSpaceDomain } from '@/services/storage'
import type { OssCredentials, StorageSpace, StorageAccessPermission } from '@/services/storage'
import type { PageResult } from '@/services/api'
import { aliyunOssRegionGroups, getAliyunOssDefaultDomain, getAliyunOssRegionId, getAliyunOssRegionLabel } from './aliyunOssRegions'
import styles from './StorageProviderPanel.module.less'

interface StorageProvider {
  key: string
  label: string
  accessLabel: string
  secretLabel: string
}

type Editor = { mode: 'add' } | { mode: 'config' } | { mode: 'domain'; space: StorageSpace } | { mode: 'delete'; space: StorageSpace }
const emptyDraft = { bucketName: '', regionId: '', accessDomain: '' }
const emptyCredentials = { accessKeyId: '', accessKeySecret: '' }

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}

function CredentialFields({ provider, value, disabled, configured, required = true, onChange }: { provider: StorageProvider; value: OssCredentials; disabled: boolean; configured: boolean; required?: boolean; onChange: (field: keyof OssCredentials, value: string) => void }) {
  return <>
    <FormField label={provider.accessLabel} htmlFor={`storage-${provider.key}-access`} required={required}><input id={`storage-${provider.key}-access`} autoComplete="off" value={value.accessKeyId} disabled={disabled} onChange={(event) => onChange('accessKeyId', event.target.value)} placeholder={`请输入${provider.accessLabel}`} required={required} /></FormField>
    <FormField label={provider.secretLabel} htmlFor={`storage-${provider.key}-secret`} required={required}><input id={`storage-${provider.key}-secret`} type="password" autoComplete="new-password" className={configured ? styles.savedSecret : undefined} value={value.accessKeySecret} disabled={disabled} onChange={(event) => onChange('accessKeySecret', event.target.value)} placeholder={configured ? '••••••••••••' : `请输入${provider.secretLabel}`} required={required} /></FormField>
  </>
}

export function StorageProviderPanel({ provider, active }: { provider: StorageProvider; active: boolean }) {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(15)
  const [editor, setEditor] = useState<Editor | null>(null)
  const [spaceDraft, setSpaceDraft] = useState(emptyDraft)
  const [accessPermission, setAccessPermission] = useState<StorageAccessPermission>('public-read')
  const [credentials, setCredentials] = useState(emptyCredentials)
  const [formError, setFormError] = useState('')
  const [pendingAction, setPendingAction] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const mounted = useRef(false)
  const actionPending = useRef(false)
  const editorVersion = useRef(0)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  const spacesQuery = useQuery({ queryKey: [...STORAGE_SPACES_QUERY_KEY, { page, pageSize }], queryFn: () => getStorageSpaces({ page, pageSize }), enabled: active, retry: false, refetchOnWindowFocus: false })
  const credentialsQuery = useQuery({ queryKey: OSS_CREDENTIALS_QUERY_KEY, queryFn: getOssCredentials, enabled: active, retry: false, refetchOnWindowFocus: false })
  const spaces = spacesQuery.data?.records ?? []
  const total = spacesQuery.data?.total ?? 0
  const configured = credentialsQuery.data?.configured === true && !credentialsQuery.isError
  const busy = Boolean(pendingAction) || spacesQuery.isFetching || credentialsQuery.isFetching

  const closeEditor = () => {
    editorVersion.current += 1
    setEditor(null)
    setFormError('')
    setCredentials(emptyCredentials)
    setSpaceDraft(emptyDraft)
    setAccessPermission('public-read')
  }
  const openEditor = (next: Editor) => {
    if (busy) return
    editorVersion.current += 1
    setFormError('')
    setSuccessMessage('')
    setCredentials(emptyCredentials)
    setSpaceDraft({ ...emptyDraft, ...('space' in next ? { bucketName: next.space.bucketName, regionId: getAliyunOssRegionId(next.space.regionId), accessDomain: next.space.accessDomain ?? '' } : {}) })
    setAccessPermission('public-read')
    setEditor(next)
  }

  // Keep credential values out of the query/mutation cache. Only configured status is cached.
  const runAction = async (label: string, action: () => Promise<string>, afterSuccess?: () => void) => {
    if (actionPending.current) return
    actionPending.current = true
    setPendingAction(label)
    setSuccessMessage('')
    const version = editorVersion.current
    try {
      const message = await action()
      if (mounted.current) {
        if (version === editorVersion.current) afterSuccess?.()
        setSuccessMessage(message)
      }
      await queryClient.invalidateQueries({ queryKey: STORAGE_SPACES_QUERY_KEY })
    } catch {
      // The shared request error Toast owns transport and business errors; retain drafts.
    } finally {
      actionPending.current = false
      if (mounted.current) setPendingAction('')
    }
  }

  const submitCredentials = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (busy) return
    const accessKeyId = credentials.accessKeyId.trim(), accessKeySecret = credentials.accessKeySecret.trim()
    if (!accessKeyId || !accessKeySecret) { setFormError('请填写完整的访问凭据'); return }
    setFormError('')
    void runAction('credentials', async () => {
      const status = await updateOssCredentials({ accessKeyId, accessKeySecret })
      queryClient.setQueryData(OSS_CREDENTIALS_QUERY_KEY, status)
      return '配置信息已保存'
    }, closeEditor)
  }

  const submitSpace = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!editor || editor.mode === 'config' || editor.mode === 'delete' || busy) return
    setFormError('')
    if (editor.mode === 'add') {
      const bucketName = spaceDraft.bucketName.trim(), regionId = spaceDraft.regionId
      if (!bucketName || !regionId) { setFormError('请输入存储空间名称并选择区域'); return }
      const accessKeyId = credentials.accessKeyId.trim(), accessKeySecret = credentials.accessKeySecret.trim()
      const hasCredentials = Boolean(accessKeyId || accessKeySecret)
      if ((!configured || hasCredentials) && (!accessKeyId || !accessKeySecret)) { setFormError('请填写完整的访问凭据'); return }
      const version = editorVersion.current
      void runAction('add', async () => {
        if (hasCredentials) {
          const status = await updateOssCredentials({ accessKeyId, accessKeySecret })
          queryClient.setQueryData(OSS_CREDENTIALS_QUERY_KEY, status)
          if (mounted.current && version === editorVersion.current) setCredentials(emptyCredentials)
        }
        await bindStorageSpace({ bucketName, regionId, accessPermission })
        return '存储空间已绑定'
      }, () => { closeEditor(); setPage(1) })
    } else {
      const id = editor.space.id
      const accessDomain = spaceDraft.accessDomain.trim() || null
      void runAction('domain', async () => {
        await updateStorageSpaceDomain(id, { accessDomain })
        return '空间域名已保存'
      }, closeEditor)
    }
  }

  const removeSpace = () => {
    if (!editor || editor.mode !== 'delete' || busy) return
    const id = editor.space.id
    void runAction('delete', async () => {
      await deleteStorageSpace(id)
      return '存储空间已删除'
    }, () => {
      closeEditor()
      setPage((current) => Math.min(current, Math.max(1, Math.ceil((total - 1) / pageSize))))
    })
  }

  const selectDefault = (space: StorageSpace) => {
    if (busy || space.isDefault) return
    void runAction('default', async () => {
      const saved = await setDefaultStorageSpace(space.id)
      queryClient.setQueriesData<PageResult<StorageSpace>>({ queryKey: STORAGE_SPACES_QUERY_KEY }, (current) => current && ({ ...current, records: current.records.map((item) => item.id === saved.id ? saved : { ...item, isDefault: false }) }))
      return '默认存储空间已更新'
    })
  }

  const editorTitle = editor?.mode === 'config' ? '配置信息' : editor?.mode === 'domain' ? '修改空间域名' : editor?.mode === 'delete' ? '删除存储空间' : '添加云空间'

  return <>
    <PaginatedListPanel page={page} pageSize={pageSize} pageSizeOptions={[15, 30, 50]} total={total} loading={busy} refreshing={spacesQuery.isFetching && Boolean(spacesQuery.data)} onPageChange={setPage} onPageSizeChange={(size) => { setPageSize(size); setPage(1) }} toolbar={<>
      <div className={styles.toolbarActions}><AppButton variant="primary" disabled={busy} onClick={() => openEditor({ mode: 'add' })}>添加存储空间</AppButton>
      <AppButton className={styles.syncButton} disabled={busy || !configured} onClick={() => void runAction('sync', async () => { const result = await syncStorageSpaces(); return `已同步 ${result.syncedCount} 个存储空间` }, () => setPage(1))}>{pendingAction === 'sync' ? '同步中…' : '同步存储空间'}</AppButton></div>
      <AppButton className={styles.configureButton} disabled={busy} onClick={() => openEditor({ mode: 'config' })}>修改配置信息</AppButton>
    </>}>
      <DataTable className={styles.table} aria-busy={spacesQuery.isFetching}>
        <caption className={styles.tableCaption}>{provider.label}存储空间</caption>
        <thead><tr><th>存储空间名称</th><th>区域</th><th>空间域名</th><th>使用状态</th><th>创建时间</th><th>更新时间</th><th>操作</th></tr></thead>
        <tbody>
          {spacesQuery.isError ? <tr><td colSpan={7} className={styles.empty}><AppButton disabled={busy} onClick={() => void spacesQuery.refetch()}>重新加载存储空间</AppButton></td></tr> : spaces.length === 0 && <tr><td colSpan={7} className={styles.empty}>{spacesQuery.isFetching ? '正在加载…' : '暂无数据'}</td></tr>}
          {!spacesQuery.isError && spaces.map((space) => <tr key={space.id}>
            <td>{space.bucketName}</td><td>{getAliyunOssRegionLabel(space.regionId)}</td><td className={styles.domain}>{space.accessDomain || getAliyunOssDefaultDomain(space.bucketName, space.regionId)}</td>
            <td><button type="button" aria-label={space.isDefault ? `${space.bucketName}为默认空间` : `将${space.bucketName}设为默认空间`} disabled={busy || space.isDefault} className={[styles.statusToggle, space.isDefault ? styles.statusToggleOn : ''].join(' ')} onClick={() => selectDefault(space)}><span /><small>{space.isDefault ? '默认' : '设为默认'}</small></button></td>
            <td className={styles.time}>{formatTime(space.createdAt)}</td><td className={styles.time}>{formatTime(space.updatedAt)}</td>
            <td><div className={styles.rowActions}><button type="button" disabled={busy} aria-label={`修改${space.bucketName}空间域名`} onClick={() => openEditor({ mode: 'domain', space })}>修改空间域名</button><AppButton type="button" variant="danger" disabled={busy || space.isDefault} title={space.isDefault ? '请先选择其他默认空间' : undefined} className={styles.deleteButton} icon={<Trash2 size={15} />} aria-label={`删除${space.bucketName}`} onClick={() => openEditor({ mode: 'delete', space })}>删除</AppButton></div></td>
          </tr>)}
        </tbody>
      </DataTable>
    </PaginatedListPanel>
    {(credentialsQuery.isError || credentialsQuery.isFetching || successMessage) && <div className={styles.statusNote}>
      {credentialsQuery.isError ? <AppButton disabled={busy} onClick={() => void credentialsQuery.refetch()}>重新查询配置状态</AppButton> : credentialsQuery.isFetching && <p role="status">正在查询配置状态…</p>}
      {successMessage && <p className={styles.successNote} role="status">{successMessage}</p>}
    </div>}

    <Drawer open={editor !== null} title={editorTitle} centered onClose={closeEditor}>
      {editor?.mode === 'config' ? <form className={styles.form} onSubmit={submitCredentials} aria-busy={Boolean(pendingAction)}>
        <CredentialFields provider={provider} value={credentials} disabled={busy} configured={configured} onChange={(field, value) => { setCredentials((current) => ({ ...current, [field]: value })); setFormError('') }} />
        {formError && <p className={styles.formError} role="alert">{formError}</p>}
        <div className={styles.formActions}><AppButton type="button" disabled={busy} onClick={closeEditor}>取消</AppButton><AppButton type="submit" variant="primary" disabled={busy}>{pendingAction === 'credentials' ? '保存中…' : '确定'}</AppButton></div>
      </form> : editor?.mode === 'delete' ? <div className={styles.form}>
        <p>确定删除存储空间“{editor.space.bucketName}”的绑定吗？</p>
        <div className={styles.formActions}><AppButton disabled={busy} onClick={closeEditor}>取消</AppButton><AppButton variant="danger" disabled={busy} onClick={removeSpace}>{pendingAction === 'delete' ? '删除中…' : '删除'}</AppButton></div>
      </div> : editor && <form className={styles.form} onSubmit={submitSpace} aria-busy={Boolean(pendingAction)}>
        {editor.mode === 'add' && <>
          <CredentialFields provider={provider} value={credentials} disabled={busy} configured={configured} required={!configured} onChange={(field, value) => { setCredentials((current) => ({ ...current, [field]: value })); setFormError('') }} />
          <FormField label="空间名称" htmlFor={`storage-${provider.key}-name`} required><input id={`storage-${provider.key}-name`} value={spaceDraft.bucketName} disabled={busy} onChange={(event) => setSpaceDraft((current) => ({ ...current, bucketName: event.target.value }))} placeholder="请输入已有 Bucket 名称" required /></FormField>
          <FormField label="空间区域" htmlFor={`storage-${provider.key}-region`} required><select id={`storage-${provider.key}-region`} value={spaceDraft.regionId} disabled={busy} onChange={(event) => setSpaceDraft((current) => ({ ...current, regionId: event.target.value }))} required><option value="" disabled>请选择空间区域</option>{aliyunOssRegionGroups.map((group) => <optgroup key={group.label} label={group.label}>{group.regions.map((region) => <option key={region.id} value={region.id}>{region.label} · {region.id}</option>)}</optgroup>)}</select></FormField>
          <FormField label="读写权限" required><div className={styles.permissionOptions} role="radiogroup" aria-label="读写权限">
            <label className={styles.permissionOption}><input type="radio" name="storagePermission" value="public-read" checked={accessPermission === 'public-read'} disabled={busy} onChange={() => setAccessPermission('public-read')} />公共读（推荐）</label>
            <label className={styles.permissionOption}><input type="radio" name="storagePermission" value="public-read-write" checked={accessPermission === 'public-read-write'} disabled={busy} onChange={() => setAccessPermission('public-read-write')} />公共读写</label>
          </div></FormField>
        </>}
        {editor.mode === 'domain' && <>
          <FormField label="空间域名" htmlFor={`storage-${provider.key}-domain`}><input id={`storage-${provider.key}-domain`} type="url" value={spaceDraft.accessDomain} disabled={busy} onChange={(event) => setSpaceDraft((current) => ({ ...current, accessDomain: event.target.value }))} placeholder="例如：https://files.example.com（可选）" /></FormField>
        </>}
        {formError && <p className={styles.formError} role="alert">{formError}</p>}
        <div className={styles.formActions}><AppButton type="button" disabled={busy} onClick={closeEditor}>取消</AppButton><AppButton type="submit" variant="primary" disabled={busy}>{pendingAction ? '保存中…' : '确定'}</AppButton></div>
      </form>}
    </Drawer>
  </>
}
