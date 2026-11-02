import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, RotateCcw, Trash2 } from 'lucide-react'
import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import {
  createSmsTemplate,
  deleteSmsTemplate,
  getSmsTemplates,
  updateSmsTemplate,
} from '@/features/messages/api'
import type {
  SmsTemplate,
  SmsTemplateInput,
  SmsTemplateKind,
  SmsTemplateProvider,
  SmsTemplateReviewStatus,
} from '@/features/messages/api'
import { AppButton, DataTable, Drawer, EmptyState, FormField, PaginatedListPanel, StatusIndicator, Toast } from '@ui/index'
import styles from './SmsTemplatesPage.module.less'

const PAGE_SIZE = 20

interface TemplateDraft extends SmsTemplateInput {
  variablesText: string
}

const emptyDraft: TemplateDraft = {
  provider: 'aliyun',
  name: '',
  templateCode: '',
  kind: 'notification',
  content: '',
  variables: [],
  variablesText: '',
  reviewStatus: 'pending',
  enabled: true,
}

const providerLabels: Record<SmsTemplateProvider, string> = {
  aliyun: '阿里云',
  tencent: '腾讯云',
}

const kindLabels: Record<SmsTemplateKind, string> = {
  verification: '验证码',
  notification: '业务通知',
  marketing: '营销通知',
}

const reviewLabels: Record<SmsTemplateReviewStatus, string> = {
  pending: '待审核',
  approved: '审核通过',
  rejected: '审核未通过',
}

function formatDateTime(value: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function toDraft(template: SmsTemplate): TemplateDraft {
  return {
    provider: template.provider,
    name: template.name,
    templateCode: template.templateCode,
    kind: template.kind,
    content: template.content,
    variables: template.variables,
    variablesText: template.variables.join(', '),
    reviewStatus: template.reviewStatus,
    enabled: template.enabled,
  }
}

export default function SmsTemplatesPage() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [provider, setProvider] = useState<'all' | SmsTemplateProvider>('all')
  const [kind, setKind] = useState<'all' | SmsTemplateKind>('all')
  const [reviewStatus, setReviewStatus] = useState<'all' | SmsTemplateReviewStatus>('all')
  const [editorOpen, setEditorOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draft, setDraft] = useState<TemplateDraft>(emptyDraft)
  const [successMessage, setSuccessMessage] = useState('')

  const query = {
    page,
    pageSize: PAGE_SIZE,
    keyword: keyword.trim() || undefined,
    provider: provider === 'all' ? undefined : provider,
    kind: kind === 'all' ? undefined : kind,
    reviewStatus: reviewStatus === 'all' ? undefined : reviewStatus,
  }
  const templatesQuery = useQuery({
    queryKey: ['sms-templates', query],
    queryFn: () => getSmsTemplates(query),
    retry: false,
    placeholderData: (previous) => previous,
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, input }: { id: number | null; input: SmsTemplateInput }) =>
      id === null ? createSmsTemplate(input) : updateSmsTemplate(id, input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['sms-templates'] })
      setEditorOpen(false)
      setSuccessMessage(editingId === null ? '短信模板已登记' : '短信模板已更新')
      setEditingId(null)
      setDraft(emptyDraft)
    },
  })
  const deleteMutation = useMutation({
    mutationFn: deleteSmsTemplate,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['sms-templates'] })
      setSuccessMessage('短信模板已删除')
    },
  })

  const openCreate = () => {
    setEditingId(null)
    setDraft(emptyDraft)
    setEditorOpen(true)
  }
  const openEdit = (template: SmsTemplate) => {
    setEditingId(template.id)
    setDraft(toDraft(template))
    setEditorOpen(true)
  }
  const closeEditor = () => {
    if (saveMutation.isPending) return
    setEditorOpen(false)
    setEditingId(null)
    setDraft(emptyDraft)
  }
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (saveMutation.isPending) return
    const input: SmsTemplateInput = {
      provider: draft.provider,
      name: draft.name.trim(),
      templateCode: draft.templateCode.trim(),
      kind: draft.kind,
      content: draft.content.trim(),
      variables: draft.variablesText.split(',').map((item) => item.trim()).filter(Boolean),
      reviewStatus: draft.reviewStatus,
      enabled: draft.enabled,
    }
    saveMutation.mutate({ id: editingId, input })
  }
  const confirmDelete = (template: SmsTemplate) => {
    if (window.confirm(`确定删除短信模板“${template.name}”吗？已被消息通知引用的模板不能删除。`)) {
      deleteMutation.mutate(template.id)
    }
  }
  const result = templatesQuery.data
  const records = result?.records ?? []

  const toolbar: ReactNode = <div className={styles.toolbar}>
    <input aria-label="搜索短信模板" value={keyword} placeholder="搜索模板名称或模板 ID" onChange={(event) => { setKeyword(event.target.value); setPage(1) }} />
    <select aria-label="短信平台筛选" value={provider} onChange={(event) => { setProvider(event.target.value as typeof provider); setPage(1) }}>
      <option value="all">全部平台</option>
      <option value="aliyun">阿里云</option>
      <option value="tencent">腾讯云</option>
    </select>
    <select aria-label="模板类型筛选" value={kind} onChange={(event) => { setKind(event.target.value as typeof kind); setPage(1) }}>
      <option value="all">全部类型</option>
      <option value="verification">验证码</option>
      <option value="notification">业务通知</option>
      <option value="marketing">营销通知</option>
    </select>
    <select aria-label="审核状态筛选" value={reviewStatus} onChange={(event) => { setReviewStatus(event.target.value as typeof reviewStatus); setPage(1) }}>
      <option value="all">全部审核状态</option>
      <option value="pending">待审核</option>
      <option value="approved">审核通过</option>
      <option value="rejected">审核未通过</option>
    </select>
    <AppButton variant="primary" icon={<Plus size={15} />} onClick={openCreate}>登记短信模板</AppButton>
  </div>

  return <div className={styles.page}>
    <div className={styles.intro}>
      <div>
        <h1>短信模板管理</h1>
        <p>集中登记各短信平台已创建的模板，并供业务消息通知选择使用。</p>
      </div>
      <p className={styles.hint}>模板需先在阿里云或腾讯云控制台创建并审核通过；本页面只维护模板资料，不直接向服务商提交审核。</p>
    </div>

    <PaginatedListPanel
      page={result?.page ?? page}
      pageSize={result?.pageSize ?? PAGE_SIZE}
      total={result?.total ?? 0}
      totalUnit="个模板"
      loading={templatesQuery.isPending}
      refreshing={templatesQuery.isFetching && !templatesQuery.isPending}
      onPageChange={setPage}
      toolbar={toolbar}
    >
      <DataTable className={styles.table}>
        <thead><tr>
          <th>ID</th><th>模板名称</th><th>短信平台</th><th>模板类型</th><th>模板 ID</th><th>模板内容</th><th>审核状态</th><th>状态</th><th>更新时间</th><th>操作</th>
        </tr></thead>
        <tbody>
          {templatesQuery.isPending && <tr><td colSpan={10}><div className={styles.state}>正在加载短信模板…</div></td></tr>}
          {templatesQuery.isError && <tr><td colSpan={10}><div className={styles.state}>
            <p role="alert">短信模板列表加载失败，请确认消息管理接口已部署。</p>
            <AppButton icon={<RotateCcw size={15} />} onClick={() => void templatesQuery.refetch()}>重试</AppButton>
          </div></td></tr>}
          {templatesQuery.isSuccess && records.length === 0 && <tr><td colSpan={10}><EmptyState description="暂无短信模板，请先登记已在服务商控制台创建的模板" /></td></tr>}
          {records.map((template) => <tr key={template.id}>
            <td className={styles.id}>{template.id}</td>
            <td className={styles.name}>{template.name}</td>
            <td>{providerLabels[template.provider]}</td>
            <td>{kindLabels[template.kind]}</td>
            <td className={styles.code}>{template.templateCode}</td>
            <td className={styles.content} title={template.content}>{template.content}</td>
            <td><StatusIndicator tone={template.reviewStatus === 'approved' ? 'success' : template.reviewStatus === 'rejected' ? 'danger' : 'warning'}>{reviewLabels[template.reviewStatus]}</StatusIndicator></td>
            <td><StatusIndicator tone={template.enabled ? 'success' : 'offline'}>{template.enabled ? '启用' : '停用'}</StatusIndicator></td>
            <td className={styles.time}>{formatDateTime(template.updatedAt)}</td>
            <td><div className={styles.rowActions}>
              <button type="button" onClick={() => openEdit(template)}>编辑</button>
              <button type="button" className={styles.deleteButton} disabled={deleteMutation.isPending} onClick={() => confirmDelete(template)}><Trash2 size={14} />删除</button>
            </div></td>
          </tr>)}
        </tbody>
      </DataTable>
    </PaginatedListPanel>

    <Drawer open={editorOpen} title={editingId === null ? '登记短信模板' : '编辑短信模板'} subtitle="登记短信平台中已经创建的模板信息" onClose={closeEditor}>
      <form className={styles.editor} onSubmit={submit} aria-busy={saveMutation.isPending}>
        <FormField label="短信平台" htmlFor="sms-template-provider" required>
          <select id="sms-template-provider" value={draft.provider} disabled={saveMutation.isPending} onChange={(event) => setDraft({ ...draft, provider: event.target.value as SmsTemplateProvider })}>
            <option value="aliyun">阿里云</option><option value="tencent">腾讯云</option>
          </select>
        </FormField>
        <FormField label="模板名称" htmlFor="sms-template-name" required>
          <input id="sms-template-name" value={draft.name} maxLength={80} disabled={saveMutation.isPending} onChange={(event) => setDraft({ ...draft, name: event.target.value })} placeholder="例如：用户下单提醒" required />
        </FormField>
        <FormField label="服务商模板 ID" htmlFor="sms-template-code" required>
          <input id="sms-template-code" value={draft.templateCode} maxLength={100} disabled={saveMutation.isPending} onChange={(event) => setDraft({ ...draft, templateCode: event.target.value })} placeholder={draft.provider === 'aliyun' ? '例如：SMS_123456789' : '填写腾讯云模板 ID'} required />
        </FormField>
        <FormField label="模板类型" htmlFor="sms-template-kind" required>
          <select id="sms-template-kind" value={draft.kind} disabled={saveMutation.isPending} onChange={(event) => setDraft({ ...draft, kind: event.target.value as SmsTemplateKind })}>
            <option value="verification">验证码</option><option value="notification">业务通知</option><option value="marketing">营销通知</option>
          </select>
        </FormField>
        <FormField label="模板内容" htmlFor="sms-template-content" required>
          <textarea id="sms-template-content" value={draft.content} maxLength={1000} rows={5} disabled={saveMutation.isPending} onChange={(event) => setDraft({ ...draft, content: event.target.value })} placeholder="填写服务商审核通过的模板内容" required />
        </FormField>
        <FormField label="模板变量" htmlFor="sms-template-variables">
          <input id="sms-template-variables" value={draft.variablesText} disabled={saveMutation.isPending} onChange={(event) => setDraft({ ...draft, variablesText: event.target.value })} placeholder="多个变量用英文逗号分隔，例如：order_id, amount" />
        </FormField>
        <FormField label="审核状态" htmlFor="sms-template-review-status" required>
          <select id="sms-template-review-status" value={draft.reviewStatus} disabled={saveMutation.isPending} onChange={(event) => setDraft({ ...draft, reviewStatus: event.target.value as SmsTemplateReviewStatus })}>
            <option value="pending">待审核</option><option value="approved">审核通过</option><option value="rejected">审核未通过</option>
          </select>
        </FormField>
        <label className={styles.enabledOption}>
          <input type="checkbox" checked={draft.enabled} disabled={saveMutation.isPending} onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })} />
          启用此模板（停用后不可被消息通知选择）
        </label>
        {saveMutation.isError && <p role="alert" className={styles.error}>保存短信模板失败，请检查接口或输入内容。</p>}
        <div className={styles.editorActions}>
          <AppButton type="button" disabled={saveMutation.isPending} onClick={closeEditor}>取消</AppButton>
          <AppButton type="submit" variant="primary" disabled={saveMutation.isPending}>{saveMutation.isPending ? '保存中…' : '保存模板'}</AppButton>
        </div>
      </form>
    </Drawer>
    <Toast open={Boolean(successMessage)}>{successMessage}</Toast>
  </div>
}
