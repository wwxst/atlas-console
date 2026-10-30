import { X } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AppButton, IconButton, Toast } from '@ui/index'
import { getStorageSettings, STORAGE_SETTINGS_QUERY_KEY, updateStorageSettings } from '@/services/storage'
import type { StorageType } from '@/services/storage'
import { StorageProviderPanel } from './StorageProviderPanel'
import styles from './StorageSettingsPage.module.less'

const providers = [
  { key: 'aliyun', label: '阿里云存储', accessLabel: 'AccessKeyId', secretLabel: 'AccessKeySecret' },
] as const

type ProviderKey = typeof providers[number]['key']
type TabKey = 'general' | ProviderKey
const tabs = [{ key: 'general', label: '存储配置' }, ...providers] as const

const providerWebsites: Record<ProviderKey, string> = {
  aliyun: 'https://www.aliyun.com',
}

export default function StorageSettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedTab = searchParams.get('tab')
  const activeTab: TabKey = providers.find(({ key }) => key === requestedTab)?.key ?? 'general'
  const provider = providers.find(({ key }) => key === activeTab)
  const queryClient = useQueryClient()
  const [draftType, setDraftType] = useState<StorageType | null>(null)
  const [successMessage, setSuccessMessage] = useState('')
  const settingsQuery = useQuery({ queryKey: STORAGE_SETTINGS_QUERY_KEY, queryFn: getStorageSettings, retry: false, refetchOnWindowFocus: false })
  const saveMutation = useMutation({ mutationFn: updateStorageSettings })
  const storageType = draftType ?? settingsQuery.data?.storageType
  const settingsBusy = settingsQuery.isFetching || saveMutation.isPending
  const saveSettings = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!storageType || settingsBusy || settingsQuery.isError) return
    setSuccessMessage('')
    saveMutation.mutate({ storageType }, { onSuccess: (result) => {
      queryClient.setQueryData(STORAGE_SETTINGS_QUERY_KEY, result.data)
      setDraftType(null)
      setSuccessMessage(result.message ?? '')
    } })
  }
  useEffect(() => {
    if (!successMessage) return
    const timer = window.setTimeout(() => setSuccessMessage(''), 4_000)
    return () => window.clearTimeout(timer)
  }, [successMessage])
  const [hiddenNotices, setHiddenNotices] = useState<Partial<Record<TabKey, boolean>>>({})

  const selectTab = (key: TabKey) => {
    const next = new URLSearchParams(searchParams)
    if (key === 'general') next.delete('tab')
    else next.set('tab', key)
    setSearchParams(next, { replace: true })
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
    document.getElementById(`storage-tab-${tabs[nextIndex].key}`)?.focus()
  }

  return <div className={styles.page}>
    <section className={styles.tabSection} aria-label="存储设置">
      <div className={styles.tabs} role="tablist" aria-label="存储配置分类">
        {tabs.map((tab, index) => <button key={tab.key} id={`storage-tab-${tab.key}`} type="button" role="tab" aria-selected={activeTab === tab.key} aria-controls={`storage-panel-${tab.key}`} tabIndex={activeTab === tab.key ? 0 : -1} className={[styles.tab, activeTab === tab.key ? styles.tabActive : ''].join(' ')} onClick={() => selectTab(tab.key)} onKeyDown={(event) => handleTabKey(event, index)}>{tab.label}</button>)}
      </div>
      {provider && !hiddenNotices[activeTab] && <div className={styles.notice}>
        <div>
          <p>{provider.label}开通方法：<a href={providerWebsites[provider.key]} target="_blank" rel="noreferrer">查看官网</a></p>
          <p>第一步：点击“添加存储空间”，在同一表单填写访问凭据、空间名称和区域；也可先修改配置信息，再同步已有空间。</p>
          <p>第二步：设为默认空间，再到存储配置中选择阿里云存储并保存。</p>
          <p>第三步（可选）：修改空间域名，并在域名服务商完成解析配置。</p>
        </div>
        <IconButton label="关闭存储提示" className={styles.closeNotice} onClick={() => setHiddenNotices((hidden) => ({ ...hidden, [activeTab]: true }))}><X size={15} /></IconButton>
      </div>}
    </section>

    <form hidden={activeTab !== 'general'} id="storage-panel-general" role="tabpanel" aria-labelledby="storage-tab-general" className={styles.configSection} onSubmit={saveSettings} aria-busy={settingsBusy}>
        <fieldset className={styles.storageOptions} disabled={settingsBusy || !settingsQuery.data || settingsQuery.isError}>
          <legend>存储方式：</legend>
          <div className={styles.radios}>
            {[{ key: 'local', label: '本地存储' }, ...providers].map((option) => <label key={option.key}><input type="radio" name="storageType" value={option.key} checked={storageType === option.key} onChange={() => { setDraftType(option.key as StorageType); setSuccessMessage('') }} /><span>{option.label}</span></label>)}
          </div>
        </fieldset>

      <div className={styles.actions}>
        <AppButton type="submit" variant="primary" disabled={settingsBusy || !storageType || settingsQuery.isError || storageType === settingsQuery.data?.storageType}>{saveMutation.isPending ? '保存中…' : '保存'}</AppButton>
        {settingsQuery.isPending && <p role="status">正在加载存储配置…</p>}
        {settingsQuery.isError && <AppButton type="button" disabled={settingsBusy} onClick={() => void settingsQuery.refetch()}>重新加载配置</AppButton>}
      </div>
    </form>
    {providers.map((item) => <div key={item.key} hidden={activeTab !== item.key} id={`storage-panel-${item.key}`} role="tabpanel" aria-labelledby={`storage-tab-${item.key}`}><StorageProviderPanel provider={item} active={activeTab === item.key} /></div>)}
    <Toast open={Boolean(successMessage)}>{successMessage}</Toast>
  </div>
}
