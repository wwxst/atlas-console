import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AppButton, FormField, Toast } from '@ui/index'
import {
  ALIYUN_SMS_SETTINGS_QUERY_KEY,
  TENCENT_SMS_SETTINGS_QUERY_KEY,
  getAliyunSmsSettings,
  getTencentSmsSettings,
  updateAliyunSmsSettings,
  updateTencentSmsSettings,
} from '@/features/settings/api'
import type { AliyunSmsSettingsUpdate, TencentSmsSettingsUpdate } from '@/features/settings/api'
import storageStyles from './StorageSettingsPage.module.less'
import formStyles from './StorageProviderPanel.module.less'

const tabs = [
  { key: 'aliyun', label: '阿里云配置' },
  { key: 'tencent', label: '腾讯云配置' },
] as const
type TabKey = typeof tabs[number]['key']

const emptyAliyun: AliyunSmsSettingsUpdate = {
  accessKeyId: '', accessKeySecret: '', signName: '', templateCode: '',
}
const emptyTencent: TencentSmsSettingsUpdate = {
  secretId: '', secretKey: '', sdkAppId: '', signName: '', templateId: '', region: 'ap-guangzhou',
}

export default function PhoneLoginSettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab: TabKey = searchParams.get('tab') === 'tencent' ? 'tencent' : 'aliyun'
  const queryClient = useQueryClient()
  const [aliyunDraftState, setAliyunDraftState] = useState<AliyunSmsSettingsUpdate | null>(null)
  const [tencentDraftState, setTencentDraftState] = useState<TencentSmsSettingsUpdate | null>(null)
  const [successMessage, setSuccessMessage] = useState('')

  const aliyunQuery = useQuery({
    queryKey: ALIYUN_SMS_SETTINGS_QUERY_KEY,
    queryFn: getAliyunSmsSettings,
    enabled: activeTab === 'aliyun',
    retry: false,
    refetchOnWindowFocus: false,
  })
  const tencentQuery = useQuery({
    queryKey: TENCENT_SMS_SETTINGS_QUERY_KEY,
    queryFn: getTencentSmsSettings,
    enabled: activeTab === 'tencent',
    retry: false,
    refetchOnWindowFocus: false,
  })

  const aliyunDraft = aliyunDraftState ?? (aliyunQuery.data ? {
    accessKeyId: aliyunQuery.data.accessKeyId,
    accessKeySecret: '',
    signName: aliyunQuery.data.signName,
    templateCode: aliyunQuery.data.templateCode,
  } : emptyAliyun)
  const tencentDraft = tencentDraftState ?? (tencentQuery.data ? {
    secretId: tencentQuery.data.secretId,
    secretKey: '',
    sdkAppId: tencentQuery.data.sdkAppId,
    signName: tencentQuery.data.signName,
    templateId: tencentQuery.data.templateId,
    region: tencentQuery.data.region,
  } : emptyTencent)

  const aliyunSave = useMutation({
    mutationFn: updateAliyunSmsSettings,
    onSuccess: (saved) => {
      queryClient.setQueryData(ALIYUN_SMS_SETTINGS_QUERY_KEY, saved)
      setAliyunDraftState(null)
      setSuccessMessage('阿里云短信配置已保存')
    },
  })
  const tencentSave = useMutation({
    mutationFn: updateTencentSmsSettings,
    onSuccess: (saved) => {
      queryClient.setQueryData(TENCENT_SMS_SETTINGS_QUERY_KEY, saved)
      setTencentDraftState(null)
      setSuccessMessage('腾讯云短信配置已保存')
    },
  })

  const selectTab = (key: TabKey) => {
    const next = new URLSearchParams(searchParams)
    if (key === 'aliyun') next.delete('tab')
    else next.set('tab', key)
    setSearchParams(next, { replace: true })
    setSuccessMessage('')
  }
  const handleTabKey = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let nextIndex: number
    if (event.key === 'ArrowRight') nextIndex = (index + 1) % tabs.length
    else if (event.key === 'ArrowLeft') nextIndex = (index + tabs.length - 1) % tabs.length
    else if (event.key === 'Home') nextIndex = 0
    else if (event.key === 'End') nextIndex = tabs.length - 1
    else return
    event.preventDefault()
    selectTab(tabs[nextIndex].key)
    document.getElementById(`phone-login-tab-${tabs[nextIndex].key}`)?.focus()
  }

  const submitAliyun = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (aliyunSave.isPending || !aliyunQuery.data) return
    setSuccessMessage('')
    aliyunSave.mutate(aliyunDraft)
  }
  const submitTencent = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (tencentSave.isPending || !tencentQuery.data) return
    setSuccessMessage('')
    tencentSave.mutate(tencentDraft)
  }

  const aliyunDirty = Boolean(aliyunDraftState && aliyunQuery.data && (
    aliyunDraft.accessKeyId !== aliyunQuery.data.accessKeyId
    || aliyunDraft.signName !== aliyunQuery.data.signName
    || aliyunDraft.templateCode !== aliyunQuery.data.templateCode
    || Boolean(aliyunDraft.accessKeySecret)
  ))
  const tencentDirty = Boolean(tencentDraftState && tencentQuery.data && (
    tencentDraft.secretId !== tencentQuery.data.secretId
    || tencentDraft.sdkAppId !== tencentQuery.data.sdkAppId
    || tencentDraft.signName !== tencentQuery.data.signName
    || tencentDraft.templateId !== tencentQuery.data.templateId
    || tencentDraft.region !== tencentQuery.data.region
    || Boolean(tencentDraft.secretKey)
  ))

  return <div className={storageStyles.page}>
    <section className={storageStyles.tabSection} aria-label="手机登录短信服务商配置">
      <div className={storageStyles.tabs} role="tablist" aria-label="短信服务商配置分类">
        {tabs.map((tab, index) => <button
          key={tab.key}
          id={`phone-login-tab-${tab.key}`}
          type="button"
          role="tab"
          aria-selected={activeTab === tab.key}
          aria-controls={`phone-login-panel-${tab.key}`}
          tabIndex={activeTab === tab.key ? 0 : -1}
          className={[storageStyles.tab, activeTab === tab.key ? storageStyles.tabActive : ''].join(' ')}
          onClick={() => selectTab(tab.key)}
          onKeyDown={(event) => handleTabKey(event, index)}
        >{tab.label}</button>)}
      </div>
    </section>

    <section hidden={activeTab !== 'aliyun'} id="phone-login-panel-aliyun" role="tabpanel" aria-labelledby="phone-login-tab-aliyun" className={storageStyles.configSection}>
      <div className={storageStyles.notice}>
        <div>
          <p>请先在阿里云短信控制台创建并审核通过短信签名和验证码模板。</p>
          <p>模板变量请使用 <code>{'${code}'}</code>，发送时系统会将验证码作为 <code>code</code> 参数传入。</p>
        </div>
        <a href="https://dysms.console.aliyun.com/" target="_blank" rel="noreferrer">打开阿里云短信控制台</a>
      </div>
      {aliyunQuery.isPending && <p role="status">正在加载阿里云短信配置…</p>}
      {aliyunQuery.isError && <p role="alert" className={formStyles.formError}>阿里云短信配置加载失败，请刷新页面重试。</p>}
      {aliyunQuery.data && <form className={formStyles.form} onSubmit={submitAliyun} aria-busy={aliyunSave.isPending}>
        <FormField label="AccessKey ID" htmlFor="aliyun-sms-access-id" required>
          <input id="aliyun-sms-access-id" autoComplete="off" value={aliyunDraft.accessKeyId} disabled={!aliyunQuery.data || aliyunSave.isPending} onChange={(event) => setAliyunDraftState({ ...aliyunDraft, accessKeyId: event.target.value }))} required />
        </FormField>
        <FormField label="AccessKey Secret" htmlFor="aliyun-sms-access-secret" required={!aliyunQuery.data.accessKeySecretConfigured}>
          <input id="aliyun-sms-access-secret" type="password" autoComplete="new-password" className={aliyunQuery.data.accessKeySecretConfigured ? formStyles.savedSecret : undefined} value={aliyunDraft.accessKeySecret} disabled={!aliyunQuery.data || aliyunSave.isPending} onChange={(event) => setAliyunDraftState({ ...aliyunDraft, accessKeySecret: event.target.value }))} placeholder={aliyunQuery.data.accessKeySecretConfigured ? '已保存，留空则保持不变' : '请输入 AccessKey Secret'} required={!aliyunQuery.data.accessKeySecretConfigured} />
        </FormField>
        <FormField label="短信签名" htmlFor="aliyun-sms-sign" required>
          <input id="aliyun-sms-sign" value={aliyunDraft.signName} disabled={!aliyunQuery.data || aliyunSave.isPending} onChange={(event) => setAliyunDraftState({ ...aliyunDraft, signName: event.target.value }))} placeholder="请输入已审核通过的签名名称" required />
        </FormField>
        <FormField label="短信模板 Code" htmlFor="aliyun-sms-template" required>
          <input id="aliyun-sms-template" value={aliyunDraft.templateCode} disabled={!aliyunQuery.data || aliyunSave.isPending} onChange={(event) => setAliyunDraftState({ ...aliyunDraft, templateCode: event.target.value }))} placeholder="例如：SMS_123456789" required />
        </FormField>
        <div className={formStyles.formActions}>
          <AppButton type="button" disabled={aliyunSave.isPending || !aliyunDirty} onClick={() => setAliyunDraftState(null)}>取消</AppButton>
          <AppButton type="submit" variant="primary" disabled={aliyunSave.isPending || !aliyunDirty}>{aliyunSave.isPending ? '保存中…' : '保存更改'}</AppButton>
        </div>
      </form>}
    </section>

    <section hidden={activeTab !== 'tencent'} id="phone-login-panel-tencent" role="tabpanel" aria-labelledby="phone-login-tab-tencent" className={storageStyles.configSection}>
      <div className={storageStyles.notice}>
        <div>
          <p>请先在腾讯云短信控制台创建并审核通过短信签名和验证码模板。</p>
          <p>模板第一个变量用于验证码，后续变量按模板顺序继续配置。</p>
        </div>
        <a href="https://console.cloud.tencent.com/smsv2" target="_blank" rel="noreferrer">打开腾讯云短信控制台</a>
      </div>
      {tencentQuery.isPending && <p role="status">正在加载腾讯云短信配置…</p>}
      {tencentQuery.isError && <p role="alert" className={formStyles.formError}>腾讯云短信配置加载失败，请刷新页面重试。</p>}
      {tencentQuery.data && <form className={formStyles.form} onSubmit={submitTencent} aria-busy={tencentSave.isPending}>
        <FormField label="SecretId" htmlFor="tencent-sms-secret-id" required>
          <input id="tencent-sms-secret-id" autoComplete="off" value={tencentDraft.secretId} disabled={!tencentQuery.data || tencentSave.isPending} onChange={(event) => setTencentDraftState({ ...tencentDraft, secretId: event.target.value }))} required />
        </FormField>
        <FormField label="SecretKey" htmlFor="tencent-sms-secret-key" required={!tencentQuery.data.secretKeyConfigured}>
          <input id="tencent-sms-secret-key" type="password" autoComplete="new-password" className={tencentQuery.data.secretKeyConfigured ? formStyles.savedSecret : undefined} value={tencentDraft.secretKey} disabled={!tencentQuery.data || tencentSave.isPending} onChange={(event) => setTencentDraftState({ ...tencentDraft, secretKey: event.target.value }))} placeholder={tencentQuery.data.secretKeyConfigured ? '已保存，留空则保持不变' : '请输入 SecretKey'} required={!tencentQuery.data.secretKeyConfigured} />
        </FormField>
        <FormField label="短信 SDK App ID" htmlFor="tencent-sms-app-id" required>
          <input id="tencent-sms-app-id" value={tencentDraft.sdkAppId} disabled={!tencentQuery.data || tencentSave.isPending} onChange={(event) => setTencentDraftState({ ...tencentDraft, sdkAppId: event.target.value }))} required />
        </FormField>
        <FormField label="短信签名" htmlFor="tencent-sms-sign" required>
          <input id="tencent-sms-sign" value={tencentDraft.signName} disabled={!tencentQuery.data || tencentSave.isPending} onChange={(event) => setTencentDraftState({ ...tencentDraft, signName: event.target.value }))} placeholder="请输入已审核通过的签名名称" required />
        </FormField>
        <FormField label="短信模板 ID" htmlFor="tencent-sms-template" required>
          <input id="tencent-sms-template" value={tencentDraft.templateId} disabled={!tencentQuery.data || tencentSave.isPending} onChange={(event) => setTencentDraftState({ ...tencentDraft, templateId: event.target.value }))} required />
        </FormField>
        <FormField label="接入地域" htmlFor="tencent-sms-region" required>
          <select id="tencent-sms-region" value={tencentDraft.region} disabled={!tencentQuery.data || tencentSave.isPending} onChange={(event) => setTencentDraftState({ ...tencentDraft, region: event.target.value }))}>
            <option value="ap-guangzhou">广州（ap-guangzhou）</option>
            <option value="ap-shanghai">上海（ap-shanghai）</option>
            <option value="ap-beijing">北京（ap-beijing）</option>
            <option value="ap-chengdu">成都（ap-chengdu）</option>
            <option value="ap-hongkong">香港（ap-hongkong）</option>
            <option value="ap-singapore">新加坡（ap-singapore）</option>
          </select>
        </FormField>
        <div className={formStyles.formActions}>
          <AppButton type="button" disabled={tencentSave.isPending || !tencentDirty} onClick={() => setTencentDraftState(null)}>取消</AppButton>
          <AppButton type="submit" variant="primary" disabled={tencentSave.isPending || !tencentDirty}>{tencentSave.isPending ? '保存中…' : '保存更改'}</AppButton>
        </div>
      </form>}
    </section>
    <Toast open={Boolean(successMessage)}>{successMessage}</Toast>
  </div>
}
