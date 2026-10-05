import { useState } from 'react'
import type { FormEvent } from 'react'
import { AppButton, DataTable, Drawer, FormField, PaginatedListPanel } from '@ui/index'
import styles from './StorageProviderPanel.module.less'

interface StorageProvider {
  key: string
  label: string
  accessLabel: string
  secretLabel: string
}

type StoragePermission = 'public-read' | 'public-read-write'
const aliyunRegions = ['华东1（杭州）', '华东2（上海）', '华北1（青岛）', '华北2（北京）', '华北3（张家口）', '华北5（呼和浩特）', '华北6（乌兰察布）', '华南1（深圳）']

interface StorageSpace {
  id: string
  name: string
  region: string
  domain: string
  permission?: StoragePermission
  enabled: boolean
  createdAt: string
  updatedAt: string
}

type Editor = { mode: 'add' } | { mode: 'config' } | { mode: 'domain'; space: StorageSpace } | { mode: 'delete'; space: StorageSpace }

function formatTime(value: string): string {
  return new Date(value).toLocaleString('sv-SE', { timeZone: 'Asia/Shanghai', hour12: false })
}

function CredentialFields({ provider, value, onChange }: { provider: StorageProvider; value: { accessKey: string; secretKey: string }; onChange: (field: 'accessKey' | 'secretKey', value: string) => void }) {
  return <>
    <FormField label={provider.accessLabel} htmlFor={`storage-${provider.key}-access`} required><input id={`storage-${provider.key}-access`} autoComplete="off" value={value.accessKey} onChange={(event) => onChange('accessKey', event.target.value)} placeholder={`请输入${provider.accessLabel}`} required /></FormField>
    <FormField label={provider.secretLabel} htmlFor={`storage-${provider.key}-secret`} required><input id={`storage-${provider.key}-secret`} type="password" autoComplete="off" value={value.secretKey} onChange={(event) => onChange('secretKey', event.target.value)} placeholder={`请输入${provider.secretLabel}`} required /></FormField>
  </>
}

export function StorageProviderPanel({ provider }: { provider: StorageProvider }) {
  const [spaces, setSpaces] = useState<StorageSpace[]>([])
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(15)
  const [editor, setEditor] = useState<Editor | null>(null)
  const [spaceDraft, setSpaceDraft] = useState({ name: '', region: '', domain: '', accessKey: '', secretKey: '', permission: 'public-read' as StoragePermission })
  const [credentials, setCredentials] = useState({ accessKey: '', secretKey: '' })
  const [formError, setFormError] = useState('')
  const visibleSpaces = spaces.slice((page - 1) * pageSize, page * pageSize)

  const openEditor = (next: Editor) => {
    setFormError('')
    setSpaceDraft({ name: '', region: '', domain: '', accessKey: '', secretKey: '', permission: 'public-read', ...('space' in next ? { name: next.space.name, region: next.space.region, domain: next.space.domain, permission: next.space.permission ?? 'public-read' } : {}) })
    setEditor(next)
  }

  const closeEditor = () => { setEditor(null); setFormError('') }

  const submitSpace = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!editor || editor.mode === 'config' || editor.mode === 'delete') return
    const domain = spaceDraft.domain.trim()
    if (editor.mode === 'add') {
      const name = spaceDraft.name.trim(), region = spaceDraft.region.trim()
      if (!spaceDraft.accessKey.trim() || !spaceDraft.secretKey.trim()) { setFormError('请输入访问密钥和密钥密码'); return }
      if (!name || !region) { setFormError('请输入存储空间名称和区域'); return }
      if (spaces.some((space) => space.name === name)) { setFormError('存储空间名称不能重复'); return }
      const now = new Date().toISOString()
      setSpaces((current) => [{ id: crypto.randomUUID(), name, region, domain, ...(provider.key === 'aliyun' ? { permission: spaceDraft.permission } : {}), enabled: false, createdAt: now, updatedAt: now }, ...current])
      setPage(1)
    } else {
      setSpaces((current) => current.map((space) => space.id === editor.space.id ? { ...space, domain, updatedAt: new Date().toISOString() } : space))
    }
    closeEditor()
  }

  const removeSpace = () => {
    if (!editor || editor.mode !== 'delete') return
    setSpaces((current) => current.filter((space) => space.id !== editor.space.id))
    setPage((current) => Math.min(current, Math.max(1, Math.ceil((spaces.length - 1) / pageSize))))
    closeEditor()
  }

  const editorTitle = editor?.mode === 'config' ? '配置信息' : editor?.mode === 'domain' ? '修改空间域名' : editor?.mode === 'delete' ? '删除存储空间' : '添加云空间'

  return <>
    <PaginatedListPanel page={page} pageSize={pageSize} pageSizeOptions={[15, 30, 50]} total={spaces.length} loading={false} onPageChange={setPage} onPageSizeChange={(size) => { setPageSize(size); setPage(1) }} toolbar={<>
      <div className={styles.toolbarActions}><AppButton variant="primary" onClick={() => openEditor({ mode: 'add' })}>添加存储空间</AppButton>
      <AppButton className={styles.syncButton} disabled title="同步功能待开放">同步存储空间</AppButton></div>
      <AppButton className={styles.configureButton} onClick={() => openEditor({ mode: 'config' })}>修改配置信息</AppButton>
    </>}>
      <DataTable className={styles.table}>
        <caption className={styles.tableCaption}>{provider.label}存储空间</caption>
        <thead><tr><th>存储空间名称</th><th>区域</th><th>空间域名</th><th>使用状态</th><th>创建时间</th><th>更新时间</th><th>操作</th></tr></thead>
        <tbody>
          {visibleSpaces.length === 0 && <tr><td colSpan={7} className={styles.empty}>暂无数据</td></tr>}
          {visibleSpaces.map((space) => <tr key={space.id}>
            <td>{space.name}</td><td>{space.region}</td><td className={styles.domain}>{space.domain || '—'}</td>
            <td><button type="button" role="switch" aria-label={`${space.name}使用状态`} aria-checked={space.enabled} className={[styles.statusToggle, space.enabled ? styles.statusToggleOn : ''].join(' ')} onClick={() => setSpaces((current) => current.map((item) => item.id === space.id ? { ...item, enabled: !item.enabled, updatedAt: new Date().toISOString() } : item))}><span /><small>{space.enabled ? '开启' : '关闭'}</small></button></td>
            <td className={styles.time}>{formatTime(space.createdAt)}</td><td className={styles.time}>{formatTime(space.updatedAt)}</td>
            <td><div className={styles.rowActions}><button type="button" aria-label={`修改${space.name}空间域名`} onClick={() => openEditor({ mode: 'domain', space })}>修改空间域名</button><button type="button" aria-label={`删除${space.name}`} onClick={() => openEditor({ mode: 'delete', space })}>删除</button></div></td>
          </tr>)}
        </tbody>
      </DataTable>
    </PaginatedListPanel>
    <p className={styles.previewNote}>页面预览：添加、状态切换、域名修改和删除仅影响当前页面；同步与保存功能待开放。</p>

    <Drawer open={editor !== null} title={editorTitle} centered onClose={closeEditor}>
      {editor?.mode === 'config' ? <form className={styles.form} onSubmit={(event) => event.preventDefault()}>
        <CredentialFields provider={provider} value={credentials} onChange={(field, value) => setCredentials((current) => ({ ...current, [field]: value }))} />
        <p className={styles.formNote}>当前为配置预览，填写内容不会保存到服务器。</p>
        <div className={styles.formActions}><AppButton type="button" onClick={closeEditor}>取消</AppButton><AppButton type="submit" variant="primary" disabled>确定</AppButton></div>
      </form> : editor?.mode === 'delete' ? <div className={styles.form}>
        <p>确定从预览中删除存储空间“{editor.space.name}”吗？</p>
        <div className={styles.formActions}><AppButton onClick={closeEditor}>取消</AppButton><AppButton variant="primary" onClick={removeSpace}>从预览移除</AppButton></div>
      </div> : editor && <form className={styles.form} onSubmit={submitSpace}>
        {editor.mode === 'add' && <>
          <CredentialFields provider={provider} value={spaceDraft} onChange={(field, value) => setSpaceDraft((current) => ({ ...current, [field]: value }))} />
          <FormField label="空间名称" htmlFor={`storage-${provider.key}-name`} required><input id={`storage-${provider.key}-name`} value={spaceDraft.name} onChange={(event) => setSpaceDraft((current) => ({ ...current, name: event.target.value }))} placeholder="请输入空间名称" required /></FormField>
          <FormField label="空间区域" htmlFor={`storage-${provider.key}-region`} required>{provider.key === 'aliyun' ? <select id={`storage-${provider.key}-region`} value={spaceDraft.region} onChange={(event) => setSpaceDraft((current) => ({ ...current, region: event.target.value }))} required><option value="" disabled>请选择空间区域</option>{aliyunRegions.map((region) => <option key={region} value={region}>{region}</option>)}</select> : <input id={`storage-${provider.key}-region`} value={spaceDraft.region} onChange={(event) => setSpaceDraft((current) => ({ ...current, region: event.target.value }))} placeholder="请输入存储区域" required />}</FormField>
          {provider.key === 'aliyun' && <fieldset className={styles.permissionRow}><legend><span className={styles.required} aria-hidden="true">*</span>读写权限<span aria-hidden="true">：</span></legend><div>{([{ value: 'public-read', label: '公共读（推荐）' }, { value: 'public-read-write', label: '公共读写' }] as const).map((option) => <label key={option.value}><input type="radio" name={`storage-${provider.key}-permission`} value={option.value} checked={spaceDraft.permission === option.value} onChange={() => setSpaceDraft((current) => ({ ...current, permission: option.value }))} /><span>{option.label}</span></label>)}</div></fieldset>}
        </>}
        {editor.mode === 'domain' && <FormField label="空间域名" htmlFor={`storage-${provider.key}-domain`}><input id={`storage-${provider.key}-domain`} type="url" value={spaceDraft.domain} onChange={(event) => setSpaceDraft((current) => ({ ...current, domain: event.target.value }))} placeholder="例如：https://files.example.com" /></FormField>}
        <p className={styles.formNote}>修改仅用于页面预览，不会创建或修改真实云存储空间。</p>
        {formError && <p className={styles.formError} role="alert">{formError}</p>}
        <div className={styles.formActions}><AppButton type="button" onClick={closeEditor}>取消</AppButton><AppButton type="submit" variant="primary" title="仅应用到当前页面预览">确定</AppButton></div>
      </form>}
    </Drawer>
  </>
}
