import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, RotateCcw } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { LOGIN_SETTINGS_QUERY_KEY, getLoginSettings, updateLoginSettings } from '@/features/settings/api'
import type { LoginSettings } from '@/features/settings/api'
import { AppButton, Toast } from '@ui/index'
import styles from './SettingsPage.module.less'

export default function SettingsPage() {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<LoginSettings | null>(null)
  const [successMessage, setSuccessMessage] = useState('')
  const settingsQuery = useQuery({
    queryKey: LOGIN_SETTINGS_QUERY_KEY,
    queryFn: getLoginSettings,
    retry: false,
    refetchOnWindowFocus: false,
  })
  const saveMutation = useMutation({
    mutationFn: updateLoginSettings,
    onMutate: () => setSuccessMessage(''),
    onSuccess: (saved) => {
      queryClient.setQueryData(LOGIN_SETTINGS_QUERY_KEY, saved)
      setDraft(null)
      setSuccessMessage('登录基础配置已保存')
    },
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: LOGIN_SETTINGS_QUERY_KEY })
    },
  })

  useEffect(() => {
    if (!successMessage) return
    const timer = window.setTimeout(() => setSuccessMessage(''), 4000)
    return () => window.clearTimeout(timer)
  }, [successMessage])

  const saved = settingsQuery.data
  const current = draft ?? saved
  const dirty = Boolean(saved && current && (
    saved.phoneLoginEnabled !== current.phoneLoginEnabled
    || saved.emailLoginEnabled !== current.emailLoginEnabled
    || saved.smsProvider !== current.smsProvider
  ))
  const disabled = settingsQuery.isFetching || settingsQuery.isError || saveMutation.isPending

  const change = (patch: Partial<LoginSettings>) => {
    if (!current || disabled) return
    setSuccessMessage('')
    setDraft({ ...current, ...patch })
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!current || !dirty || disabled) return
    saveMutation.mutate(current)
  }

  return <div className={styles.page}>
    <section className={styles.tabSection} aria-label="登录设置">
      <div className={styles.tabs}>
        <span className={[styles.tab, styles.tabActive].join(' ')}>登录基础配置</span>
      </div>
    </section>

    {settingsQuery.isPending && <div className={styles.state}><span className={styles.spinner} /><p>正在加载登录配置…</p></div>}
    {settingsQuery.isError && <div className={styles.state} role="alert">
      <AlertCircle size={32} />
      <p>登录基础配置加载失败，请重试。</p>
      <AppButton onClick={() => void settingsQuery.refetch()} disabled={settingsQuery.isFetching}>重新加载</AppButton>
    </div>}

    {current && <form className={styles.configSection} onSubmit={submit} aria-busy={saveMutation.isPending}>
      <div className={styles.settingRows}>
        <div className={styles.loginSettingRow}>
          <div className={styles.loginSettingInfo}>
            <strong>手机登录</strong>
            <small>控制普通用户是否可以使用手机号登录</small>
          </div>
          <span className={[styles.status, current.phoneLoginEnabled ? styles.statusOn : styles.statusOff].join(' ')}>
            {current.phoneLoginEnabled ? '已启用' : '已停用'}
          </span>
          <button
            type="button"
            role="switch"
            aria-checked={current.phoneLoginEnabled}
            aria-label="手机登录"
            className={[styles.toggle, current.phoneLoginEnabled ? styles.toggleOn : ''].join(' ')}
            disabled={disabled}
            onClick={() => change({ phoneLoginEnabled: !current.phoneLoginEnabled })}
          ><span className={styles.toggleThumb} /></button>
        </div>

        <fieldset className={styles.storageOptions} disabled={disabled}>
          <legend>短信服务平台：</legend>
          <div className={styles.radios}>
            <label><input type="radio" name="smsProvider" value="aliyun" checked={current.smsProvider === 'aliyun'} onChange={() => change({ smsProvider: 'aliyun' })} />阿里云短信</label>
            <label><input type="radio" name="smsProvider" value="tencent" checked={current.smsProvider === 'tencent'} onChange={() => change({ smsProvider: 'tencent' })} />腾讯云短信</label>
          </div>
          <p className={styles.helperText}>选择实际用于发送手机登录验证码的短信平台，具体密钥和模板在“手机登录配置”中填写。</p>
        </fieldset>

        <div className={styles.loginSettingRow}>
          <div className={styles.loginSettingInfo}>
            <strong>邮箱登录</strong>
            <small>控制普通用户是否可以使用邮箱登录</small>
          </div>
          <span className={[styles.status, current.emailLoginEnabled ? styles.statusOn : styles.statusOff].join(' ')}>
            {current.emailLoginEnabled ? '已启用' : '已停用'}
          </span>
          <button
            type="button"
            role="switch"
            aria-checked={current.emailLoginEnabled}
            aria-label="邮箱登录"
            className={[styles.toggle, current.emailLoginEnabled ? styles.toggleOn : ''].join(' ')}
            disabled={disabled}
            onClick={() => change({ emailLoginEnabled: !current.emailLoginEnabled })}
          ><span className={styles.toggleThumb} /></button>
        </div>
      </div>

      <div className={styles.actions}>
        <AppButton type="button" variant="secondary" disabled={!dirty || disabled} onClick={() => setDraft(null)}>取消</AppButton>
        <AppButton type="submit" variant="primary" disabled={!dirty || disabled}>
          {saveMutation.isPending ? '保存中…' : '保存更改'}
        </AppButton>
      </div>
    </form>}

    <Toast open={Boolean(successMessage)}>{successMessage}</Toast>
  </div>
}
