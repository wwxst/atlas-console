import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { AppButton, FormField, Toast } from '@ui/index'
import {
  EMAIL_LOGIN_SETTINGS_QUERY_KEY,
  getEmailLoginSettings,
  updateEmailLoginSettings,
} from '@/features/settings/api'
import type { EmailLoginSettingsUpdate, SmtpSecurity } from '@/features/settings/api'
import storageStyles from './StorageSettingsPage.module.less'
import formStyles from './StorageProviderPanel.module.less'

const emptyDraft: EmailLoginSettingsUpdate = {
  host: '',
  port: 465,
  username: '',
  password: '',
  fromAddress: '',
  fromName: 'Atlas',
  security: 'SSL',
}

export default function EmailLoginSettingsPage() {
  const queryClient = useQueryClient()
  const [draftState, setDraftState] = useState<EmailLoginSettingsUpdate | null>(null)
  const [successMessage, setSuccessMessage] = useState('')
  const settingsQuery = useQuery({
    queryKey: EMAIL_LOGIN_SETTINGS_QUERY_KEY,
    queryFn: getEmailLoginSettings,
    retry: false,
    refetchOnWindowFocus: false,
  })
  const saveMutation = useMutation({
    mutationFn: updateEmailLoginSettings,
    onSuccess: (saved) => {
      queryClient.setQueryData(EMAIL_LOGIN_SETTINGS_QUERY_KEY, saved)
      setDraftState(null)
      setSuccessMessage('邮箱 SMTP 配置已保存')
    },
  })

  const saved = settingsQuery.data
  const draft = draftState ?? (saved ? {
    host: saved.host,
    port: saved.port,
    username: saved.username,
    password: '',
    fromAddress: saved.fromAddress,
    fromName: saved.fromName,
    security: saved.security,
  } : emptyDraft)
  const dirty = Boolean(saved && draftState && (
    draft.host !== saved.host
    || draft.port !== saved.port
    || draft.username !== saved.username
    || draft.fromAddress !== saved.fromAddress
    || draft.fromName !== saved.fromName
    || draft.security !== saved.security
    || Boolean(draft.password)
  ))

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!dirty || !saved || saveMutation.isPending) return
    setSuccessMessage('')
    saveMutation.mutate(draft)
  }

  const resetDraft = () => {
    if (!saved) return
    setDraftState(null)
  }

  return <div className={storageStyles.page}>
    <section className={storageStyles.tabSection} aria-label="邮箱登录配置">
      <div className={storageStyles.tabs}>
        <span className={[storageStyles.tab, storageStyles.tabActive].join(' ')}>邮箱服务配置</span>
      </div>
    </section>

    <section className={storageStyles.configSection}>
      <div className={storageStyles.notice}>
        <div>
          <p>配置 SMTP 邮件服务器，用于发送邮箱验证码。</p>
          <p>建议使用邮箱服务商提供的 SMTP 授权码，不要填写邮箱网页登录密码。</p>
        </div>
      </div>
      {settingsQuery.isPending && <p role="status">正在加载邮箱服务配置…</p>}
      {settingsQuery.isError && <p role="alert" className={formStyles.formError}>邮箱服务配置加载失败，请刷新页面重试。</p>}
      {saved && <form className={formStyles.form} onSubmit={submit} aria-busy={saveMutation.isPending}>
        <FormField label="SMTP 服务器" htmlFor="email-smtp-host" required>
          <input id="email-smtp-host" autoComplete="off" value={draft.host} disabled={!saved || saveMutation.isPending} onChange={(event) => setDraftState({ ...draft, host: event.target.value })} placeholder="例如：smtp.example.com" required />
        </FormField>
        <FormField label="SMTP 端口" htmlFor="email-smtp-port" required>
          <input id="email-smtp-port" type="number" min={1} max={65535} value={draft.port} disabled={!saved || saveMutation.isPending} onChange={(event) => setDraftState({ ...draft, port: Number(event.target.value) }))} required />
        </FormField>
        <FormField label="SMTP 用户名" htmlFor="email-smtp-username" required>
          <input id="email-smtp-username" autoComplete="username" value={draft.username} disabled={!saved || saveMutation.isPending} onChange={(event) => setDraftState({ ...draft, username: event.target.value })} required />
        </FormField>
        <FormField label="SMTP 密码 / 授权码" htmlFor="email-smtp-password" required={!saved.passwordConfigured}>
          <input id="email-smtp-password" type="password" autoComplete="new-password" className={saved.passwordConfigured ? formStyles.savedSecret : undefined} value={draft.password} disabled={!saved || saveMutation.isPending} onChange={(event) => setDraftState({ ...draft, password: event.target.value })} placeholder={saved.passwordConfigured ? '已保存，留空则保持不变' : '请输入 SMTP 密码或授权码'} required={!saved.passwordConfigured} />
        </FormField>
        <FormField label="发件邮箱" htmlFor="email-smtp-from" required>
          <input id="email-smtp-from" type="email" autoComplete="email" value={draft.fromAddress} disabled={!saved || saveMutation.isPending} onChange={(event) => setDraftState({ ...draft, fromAddress: event.target.value })} placeholder="例如：no-reply@example.com" required />
        </FormField>
        <FormField label="发件人名称" htmlFor="email-smtp-from-name" required>
          <input id="email-smtp-from-name" value={draft.fromName} disabled={!saved || saveMutation.isPending} onChange={(event) => setDraftState({ ...draft, fromName: event.target.value })} placeholder="例如：Atlas" required />
        </FormField>
        <FormField label="SMTP 加密方式" htmlFor="email-smtp-security" required>
          <select id="email-smtp-security" value={draft.security} disabled={!saved || saveMutation.isPending} onChange={(event) => setDraftState({ ...draft, security: event.target.value as SmtpSecurity })}>
            <option value="SSL">SSL（通常使用 465 端口）</option>
            <option value="STARTTLS">STARTTLS（通常使用 587 端口）</option>
            <option value="NONE">不加密（不推荐）</option>
          </select>
        </FormField>
        <div className={formStyles.formActions}>
          <AppButton type="button" disabled={!saved || !dirty || saveMutation.isPending} onClick={resetDraft}>取消</AppButton>
          <AppButton type="submit" variant="primary" disabled={!dirty || saveMutation.isPending}>{saveMutation.isPending ? '保存中…' : '保存更改'}</AppButton>
        </div>
      </form>}
    </section>
    <Toast open={Boolean(successMessage)}>{successMessage}</Toast>
  </div>
}
