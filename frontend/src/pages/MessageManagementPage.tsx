import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, RotateCcw, Settings2, Trash2 } from 'lucide-react'
import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { getLoginSettings, LOGIN_SETTINGS_QUERY_KEY } from '@/features/settings/api'
import {
  createMessageNotification,
  deleteMessageNotification,
  getMessageNotifications,
  getSmsTemplates,
  updateMessageNotification,
} from '@/features/messages/api'
import type {
  MessageCategory,
  MessageChannel,
  MessageNotification,
  MessageNotificationInput,
  SmsTemplate,
} from '@/features/messages/api'
import { AppButton, DataTable, Drawer, EmptyState, FormField, StatusIndicator, Toast } from '@ui/index'
import styles from './MessageManagementPage.module.less'

const categories: Array<{ key: MessageCategory; label: string }> = [
  { key: 'member', label: '会员通知' },
  { key: 'platform', label: '平台通知' },
  { key: 'custom', label: '自定义通知' },
]

const detailTabs = [
  { key: 'system', label: '系统通知' },
  { key: 'sms', label: '短信通知' },
  { key: 'wechat', label: '微信模板消息' },
  { key: 'wecom', label: '企业微信' },
] as const
type DetailTab = typeof detailTabs[number]['key']

const emptyInput: MessageNotificationInput = {
  category: 'custom',
  code: '',
  name: '',
  description: '',
  channels: { siteMessage: true, officialAccount: false, sms: false, enterpriseWechat: false },
  siteMessageTitle: '',
  siteMessageContent: '',
  smsTemplateId: null,
  smsVariableMappings: {},
}

const channelLabels: Record<MessageChannel, string> = {
  siteMessage: '站内信',
  officialAccount: '公众号模板',
  sms: '发送短信',
  enterpriseWechat: '企业微信',
}

function toInput(item: MessageNotification): MessageNotificationInput {
  return {
    category: item.category,
    code: item.code,
    name: item.name,
    description: item.description,
    channels: { ...item.channels },
    siteMessageTitle: item.siteMessageTitle,
    siteMessageContent: item.siteMessageContent,
    smsTemplateId: item.smsTemplateId,
    smsVariableMappings: { ...item.smsVariableMappings },
  }
}

function formatDateTime(value: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

function Toggle({ checked, label, disabled, onChange }: {
  checked: boolean
  label: string
  disabled?: boolean
  onChange: (next: boolean) => void
}) {
  return <button
    type="button"
    role="switch"
    aria-checked={checked}
    aria-label={label}
    disabled={disabled}
    className={[styles.toggle, checked ? styles.toggleOn : ''].join(' ')}
    onClick={() => onChange(!checked)}
  ><span /></button>
}

export default function MessageManagementPage() {
  const queryClient = useQueryClient()
  const [category, setCategory] = useState<MessageCategory>('member')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draft, setDraft] = useState<MessageNotificationInput | null>(null)
  const [detailTab, setDetailTab] = useState<DetailTab>('system')
  const [successMessage, setSuccessMessage] = useState('')
  const [formError, setFormError] = useState('')

  const notificationsQuery = useQuery({
    queryKey: ['message-notifications', category],
    queryFn: () => getMessageNotifications(category),
    retry: false,
  })
  const loginSettingsQuery = useQuery({
    queryKey: LOGIN_SETTINGS_QUERY_KEY,
    queryFn: getLoginSettings,
    retry: false,
    enabled: drawerOpen && detailTab === 'sms',
  })
  const templatesQuery = useQuery({
    queryKey: ['sms-templates', 'approved-for-notifications'],
    queryFn: () => getSmsTemplates({ page: 1, pageSize: 100, reviewStatus: 'approved' }),
    retry: false,
    enabled: drawerOpen && detailTab === 'sms',
  })

  const saveMutation = useMutation({
    mutationFn: ({ id, input }: { id: number | null; input: MessageNotificationInput }) =>
      id === null ? createMessageNotification(input) : updateMessageNotification(id, input),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['message-notifications'] })
      setDrawerOpen(false)
      setDraft(null)
      setEditingId(null)
      setSuccessMessage(editingId === null ? '自定义通知已创建' : '通知配置已保存')
    },
  })
  const channelMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: MessageNotificationInput }) => updateMessageNotification(id, input),
    onSuccess: (saved) => {
      queryClient.setQueryData<MessageNotification[]>(['message-notifications', saved.category], (current) =>
        current?.map((item) => item.id === saved.id ? saved : item),
      )
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: ['message-notifications', category] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: deleteMessageNotification,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['message-notifications', category] })
      setSuccessMessage('自定义通知已删除')
    },
  })

  const templates = (templatesQuery.data?.records ?? []).filter((item: SmsTemplate) =>
    item.enabled && item.reviewStatus === 'approved' &&
    (!loginSettingsQuery.data || item.provider === loginSettingsQuery.data.smsProvider),
  )
  const selectedTemplate = templates.find((item) => item.id === draft?.smsTemplateId)

  const openEditor = (item: MessageNotification) => {
    setFormError('')
    setEditingId(item.id)
    setDraft(toInput(item))
    setDetailTab('system')
    setDrawerOpen(true)
  }
  const openCreate = () => {
    setFormError('')
    setEditingId(null)
    setDraft({ ...emptyInput, category: 'custom' })
    setDetailTab('system')
    setDrawerOpen(true)
  }
  const closeEditor = () => {
    if (saveMutation.isPending) return
    setFormError('')
    setDrawerOpen(false)
    setDraft(null)
    setEditingId(null)
  }
  const updateDraft = (patch: Partial<MessageNotificationInput>) => {
    setFormError('')
    setDraft((current) => current ? { ...current, ...patch } : current)
  }
  const updateChannels = (channels: Partial<MessageNotificationInput['channels']>) => {
    setFormError('')
    setDraft((current) => current ? { ...current, channels: { ...current.channels, ...channels } } : current)
  }
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!draft || saveMutation.isPending) return
    if (draft.channels.sms) {
      if (!draft.smsTemplateId || !selectedTemplate) {
        setDetailTab('sms')
        setFormError('启用短信通知前，请选择当前短信平台下审核通过且已启用的模板。')
        return
      }
      const missingVariables = selectedTemplate.variables.filter((variable) => !draft.smsVariableMappings[variable]?.trim())
      if (missingVariables.length > 0) {
        setDetailTab('sms')
        setFormError(`请填写短信模板变量映射：${missingVariables.join('、')}`)
        return
      }
    }
    setFormError('')
    saveMutation.mutate({ id: editingId, input: draft })
  }
  const toggleChannel = (item: MessageNotification, channel: MessageChannel, next: boolean) => {
    if (channel === 'sms' && next && !item.smsTemplateId) {
      openEditor(item)
      setDetailTab('sms')
      return
    }
    const input = toInput(item)
    input.channels = { ...input.channels, [channel]: next }
    channelMutation.mutate({ id: item.id, input })
  }
  const confirmDelete = (item: MessageNotification) => {
    if (window.confirm(`确定删除自定义通知“${item.name}”吗？`)) deleteMutation.mutate(item.id)
  }

  const records = notificationsQuery.data ?? []
  const toolbar: ReactNode = category === 'custom'
    ? <AppButton variant="primary" icon={<Plus size={15} />} onClick={openCreate}>新增自定义通知</AppButton>
    : <span className={styles.toolbarHint}>在对应业务场景中设置通知渠道和短信模板</span>

  return <div className={styles.page}>
    <section className={styles.tabSection} aria-label="消息管理">
      <div className={styles.tabs} role="tablist" aria-label="通知分类">
        {categories.map((item) => <button
          key={item.key}
          type="button"
          role="tab"
          aria-selected={category === item.key}
          className={[styles.tab, category === item.key ? styles.tabActive : ''].join(' ')}
          onClick={() => setCategory(item.key)}
        >{item.label}</button>)}
      </div>
    </section>

    <section className={styles.listSection}>
      <div className={styles.toolbar}>{toolbar}</div>
      <div className={styles.tableWrap}>
        <DataTable className={styles.table}>
          <thead><tr>
            <th>ID</th><th>通知类型</th><th>通知场景说明</th><th>站内信</th><th>公众号模板</th><th>发送短信</th><th>企业微信</th><th>操作</th>
          </tr></thead>
          <tbody>
            {notificationsQuery.isPending && <tr><td colSpan={8}><div className={styles.state}>正在加载通知场景…</div></td></tr>}
            {notificationsQuery.isError && <tr><td colSpan={8}><div className={styles.state}>
              <p role="alert">消息通知列表加载失败，请确认消息管理接口已部署。</p>
              <AppButton icon={<RotateCcw size={15} />} onClick={() => void notificationsQuery.refetch()}>重试</AppButton>
            </div></td></tr>}
            {notificationsQuery.isSuccess && records.length === 0 && <tr><td colSpan={8}><EmptyState description={category === 'custom' ? '暂无自定义通知，点击“新增自定义通知”创建' : '当前分类暂无通知场景'} /></td></tr>}
            {records.map((item) => <tr key={item.id}>
              <td className={styles.id}>{item.id}</td>
              <td className={styles.name}>{item.name}</td>
              <td className={styles.description} title={item.description}>{item.description}</td>
              {(['siteMessage', 'officialAccount', 'sms', 'enterpriseWechat'] as MessageChannel[]).map((channel) =>
                <td key={channel}><Toggle
                  checked={item.channels[channel]}
                  label={`${item.name} - ${channelLabels[channel]}`}
                  disabled={channelMutation.isPending}
                  onChange={(next) => toggleChannel(item, channel, next)}
                /></td>,
              )}
              <td><div className={styles.rowActions}>
                <button type="button" onClick={() => openEditor(item)}><Settings2 size={14} />设置</button>
                {item.category === 'custom' && <button type="button" className={styles.deleteButton} disabled={deleteMutation.isPending} onClick={() => confirmDelete(item)}><Trash2 size={14} />删除</button>}
              </div></td>
            </tr>)}
          </tbody>
        </DataTable>
      </div>
      <div className={styles.footer}>
        <span>共 {records.length} 个通知场景</span>
        {notificationsQuery.dataUpdatedAt > 0 && <span>最近刷新：{formatDateTime(new Date(notificationsQuery.dataUpdatedAt).toISOString())}</span>}
      </div>
    </section>

    <Drawer open={drawerOpen} title={editingId === null ? '新增自定义通知' : '通知场景设置'} subtitle={draft?.description || '配置业务场景对应的消息渠道'} size="wide" onClose={closeEditor}>
      {draft && <form className={styles.editor} onSubmit={submit} aria-busy={saveMutation.isPending}>
        {editingId === null && <div className={styles.customFields}>
          <FormField label="通知编码" htmlFor="message-notification-code" required>
            <input id="message-notification-code" value={draft.code} maxLength={80} disabled={saveMutation.isPending} onChange={(event) => updateDraft({ code: event.target.value })} placeholder="例如：order_paid_notice" required />
          </FormField>
          <FormField label="通知名称" htmlFor="message-notification-name" required>
            <input id="message-notification-name" value={draft.name} maxLength={80} disabled={saveMutation.isPending} onChange={(event) => updateDraft({ name: event.target.value })} placeholder="例如：订单支付成功通知" required />
          </FormField>
          <FormField label="场景说明" htmlFor="message-notification-description" required>
            <input id="message-notification-description" value={draft.description} maxLength={200} disabled={saveMutation.isPending} onChange={(event) => updateDraft({ description: event.target.value })} required />
          </FormField>
        </div>}

        <div className={styles.detailTabs} role="tablist" aria-label="通知渠道配置">
          {detailTabs.map((tab) => <button key={tab.key} type="button" role="tab" aria-selected={detailTab === tab.key} className={detailTab === tab.key ? styles.detailTabActive : ''} onClick={() => setDetailTab(tab.key)}>{tab.label}</button>)}
        </div>

        {detailTab === 'system' && <div className={styles.detailPanel}>
          <label className={styles.channelOption}>
            <input type="checkbox" checked={draft.channels.siteMessage} disabled={saveMutation.isPending} onChange={(event) => updateChannels({ siteMessage: event.target.checked })} />
            启用站内信通知
          </label>
          <FormField label="通知标题" htmlFor="message-site-title">
            <input id="message-site-title" value={draft.siteMessageTitle} disabled={saveMutation.isPending || !draft.channels.siteMessage} onChange={(event) => updateDraft({ siteMessageTitle: event.target.value })} placeholder="请输入站内信标题" />
          </FormField>
          <FormField label="通知内容" htmlFor="message-site-content">
            <textarea id="message-site-content" rows={5} value={draft.siteMessageContent} disabled={saveMutation.isPending || !draft.channels.siteMessage} onChange={(event) => updateDraft({ siteMessageContent: event.target.value })} placeholder="支持使用业务变量，具体变量由后端场景提供" />
          </FormField>
        </div>}

        {detailTab === 'sms' && <div className={styles.detailPanel}>
          <label className={styles.channelOption}>
            <input type="checkbox" checked={draft.channels.sms} disabled={saveMutation.isPending} onChange={(event) => updateChannels({ sms: event.target.checked })} />
            启用短信通知
          </label>
          <div className={styles.providerSummary}>
            <span>当前短信服务平台</span>
            {loginSettingsQuery.isPending ? <span>正在读取…</span>
              : loginSettingsQuery.data ? <strong>{loginSettingsQuery.data.smsProvider === 'aliyun' ? '阿里云' : '腾讯云'}</strong>
                : <span className={styles.muted}>暂时无法读取登录基础配置</span>}
          </div>
          {templatesQuery.isPending && <p role="status" className={styles.muted}>正在加载审核通过的短信模板…</p>}
          {templatesQuery.isError && <p role="alert" className={styles.error}>短信模板列表加载失败，请重试。</p>}
          <FormField label="短信模板" htmlFor="message-sms-template">
            <select id="message-sms-template" value={draft.smsTemplateId ?? ''} disabled={saveMutation.isPending || !draft.channels.sms || templatesQuery.isPending || !loginSettingsQuery.data} onChange={(event) => updateDraft({ smsTemplateId: event.target.value ? Number(event.target.value) : null, smsVariableMappings: {} })}>
              <option value="">请选择审核通过的短信模板</option>
              {templates.map((template) => <option key={template.id} value={template.id}>{template.name} · {template.templateCode}</option>)}
            </select>
          </FormField>
          {templatesQuery.isSuccess && draft.smsTemplateId && !selectedTemplate && <p role="alert" className={styles.error}>当前关联模板不可用或不属于已选短信平台，请重新选择。</p>}
          {selectedTemplate && <div className={styles.templatePreview}>
            <div><span>模板 ID</span><strong>{selectedTemplate.templateCode}</strong></div>
            <p>{selectedTemplate.content}</p>
          </div>}
          {selectedTemplate?.variables.map((variable) => <FormField key={variable} label={`变量 ${variable}`} htmlFor={`message-sms-variable-${variable}`}>
            <input id={`message-sms-variable-${variable}`} value={draft.smsVariableMappings[variable] ?? ''} disabled={saveMutation.isPending || !draft.channels.sms} onChange={(event) => updateDraft({ smsVariableMappings: { ...draft.smsVariableMappings, [variable]: event.target.value } })} placeholder="填写对应的业务数据字段，例如 orderNo" />
          </FormField>)}
          <p className={styles.muted}>只有审核通过且处于启用状态、并且与当前短信服务平台一致的模板才能被选择。</p>
        </div>}

        {detailTab === 'wechat' && <div className={styles.detailPanel}>
          <label className={styles.channelOption}>
            <input type="checkbox" checked={draft.channels.officialAccount} disabled={saveMutation.isPending} onChange={(event) => updateChannels({ officialAccount: event.target.checked })} />
            启用公众号模板消息
          </label>
          <div className={styles.channelPlaceholder}>
            <StatusIndicator tone="warning">待接入</StatusIndicator>
            <p>公众号模板消息的模板库和变量映射将在微信服务配置接入后开放。当前可先维护此业务场景的渠道开关。</p>
          </div>
        </div>}

        {detailTab === 'wecom' && <div className={styles.detailPanel}>
          <label className={styles.channelOption}>
            <input type="checkbox" checked={draft.channels.enterpriseWechat} disabled={saveMutation.isPending} onChange={(event) => updateChannels({ enterpriseWechat: event.target.checked })} />
            启用企业微信通知
          </label>
          <div className={styles.channelPlaceholder}>
            <StatusIndicator tone="warning">待接入</StatusIndicator>
            <p>企业微信应用和消息模板配置将在对应平台接入后开放。当前可先维护此业务场景的渠道开关。</p>
          </div>
        </div>}

        {formError && <p role="alert" className={styles.error}>{formError}</p>}
        {saveMutation.isError && <p role="alert" className={styles.error}>通知配置保存失败，请检查消息管理接口或稍后重试。</p>}
        <div className={styles.editorActions}>
          <AppButton type="button" disabled={saveMutation.isPending} onClick={closeEditor}>取消</AppButton>
          <AppButton type="submit" variant="primary" disabled={saveMutation.isPending}>{saveMutation.isPending ? '保存中…' : '保存配置'}</AppButton>
        </div>
      </form>}
    </Drawer>
    <Toast open={Boolean(successMessage)}>{successMessage}</Toast>
  </div>
}
