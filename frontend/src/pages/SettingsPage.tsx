import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, CheckCircle2, Mail, RotateCcw, Smartphone } from 'lucide-react'
import { useEffect, useId, useState } from 'react'
import type { FormEvent } from 'react'
import { AUTH_CHANNELS_QUERY_KEY, getAuthChannels, updateAuthChannel } from '@/features/settings/api'
import type { AuthChannel, AuthChannels, AuthChannelUpdate } from '@/features/settings/api'
import { AppButton, Toast } from '@ui/index'
import styles from './SettingsPage.module.less'

interface ChannelSettingsProps {
  channel: AuthChannel
  saved: AuthChannelUpdate
  otherEnabled: boolean
  disabled: boolean
  saving: boolean
  onSave: (update: AuthChannelUpdate, onSuccess: () => void) => void
}

function ChannelSettings({ channel, saved, otherEnabled, disabled, saving, onSave }: ChannelSettingsProps) {
  const [draft, setDraft] = useState<AuthChannelUpdate | null>(null)
  const warningId = useId()
  const current = draft ?? saved
  const dirty = current.enabled !== saved.enabled || current.codeEnabled !== saved.codeEnabled
  const blocksAllChannels = !current.enabled && !otherEnabled
  const title = channel === 'phone' ? '手机号' : '邮箱'
  const Icon = channel === 'phone' ? Smartphone : Mail

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (disabled || !dirty || blocksAllChannels) return
    onSave(current, () => setDraft(null))
  }

  return <form className={styles.channelCard} onSubmit={submit} aria-label={`${title}渠道设置`}>
    <div className={styles.cardIcon}>
      <Icon size={28} strokeWidth={1.5} aria-hidden="true" />
    </div>

    <div className={styles.cardContent}>
      <div className={styles.cardHeader}>
        <div>
          <h3>{title}认证</h3>
          <p>用户可使用{title}进行注册、登录和账号绑定</p>
        </div>
        <div className={`${styles.statusPill} ${saved.enabled ? styles.statusEnabled : styles.statusDisabled}`}>
          {saved.enabled ? <CheckCircle2 size={14} /> : <AlertCircle size={14} />}
          <span>{saved.enabled ? '已启用' : '已停用'}</span>
        </div>
      </div>

      <div className={styles.settingsGrid}>
        <label className={styles.setting}>
          <div className={styles.settingInfo}>
            <strong>渠道状态</strong>
            <small>关闭后该{title}渠道的所有认证能力将停止</small>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={current.enabled}
            aria-label={`${current.enabled ? '停用' : '启用'}${title}渠道`}
            className={`${styles.toggle} ${current.enabled ? styles.toggleOn : ''}`}
            disabled={disabled}
            onClick={() => setDraft({ ...current, enabled: !current.enabled })}
            aria-describedby={blocksAllChannels ? warningId : undefined}
          >
            <span className={styles.toggleThumb} />
          </button>
        </label>

        <label className={styles.setting}>
          <div className={styles.settingInfo}>
            <strong>验证码登录</strong>
            <small>允许用户通过{title}验证码进行免密码登录</small>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={current.codeEnabled}
            aria-label={`${current.codeEnabled ? '停用' : '启用'}验证码登录`}
            className={`${styles.toggle} ${current.codeEnabled ? styles.toggleOn : ''}`}
            disabled={disabled || !current.enabled}
            onClick={() => setDraft({ ...current, codeEnabled: !current.codeEnabled })}
          >
            <span className={styles.toggleThumb} />
          </button>
        </label>
      </div>

      <div className={styles.messageSlot}>
        {blocksAllChannels && (
          <div className={styles.warningBanner} role="alert" id={warningId}>
            <AlertCircle size={16} />
            <span>至少需要保留一个认证渠道开启，请先启用另一个渠道</span>
          </div>
        )}

        {dirty && !blocksAllChannels && (
          <div className={styles.draftBanner}>
            <span>有未保存的修改</span>
          </div>
        )}
      </div>
    </div>

    <div className={styles.cardActions}>
      <AppButton type="button" variant="ghost" disabled={disabled || !dirty} onClick={() => setDraft(null)}>
        取消
      </AppButton>
      <AppButton type="submit" variant="primary" disabled={disabled || !dirty || blocksAllChannels}>
        {saving ? '保存中…' : '保存更改'}
      </AppButton>
    </div>
  </form>
}

export default function SettingsPage() {
  const queryClient = useQueryClient()
  const [successMessage, setSuccessMessage] = useState('')
  const channelsQuery = useQuery({ queryKey: AUTH_CHANNELS_QUERY_KEY, queryFn: getAuthChannels, retry: false })
  const saveMutation = useMutation({
    mutationFn: ({ channel, update }: { channel: AuthChannel; update: AuthChannelUpdate }) =>
      updateAuthChannel(channel, update),
    onMutate: () => setSuccessMessage(''),
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: AUTH_CHANNELS_QUERY_KEY })
    },
    onSuccess: (_, { channel, update }) => {
      queryClient.setQueryData<AuthChannels>(AUTH_CHANNELS_QUERY_KEY, (saved) => {
        if (!saved) return saved
        return channel === 'phone'
          ? { ...saved, phoneEnabled: update.enabled, phoneCodeEnabled: update.codeEnabled }
          : { ...saved, emailEnabled: update.enabled, emailCodeEnabled: update.codeEnabled }
      })
      setSuccessMessage(`${channel === 'phone' ? '手机号' : '邮箱'}渠道设置已保存`)
      void queryClient.invalidateQueries({ queryKey: AUTH_CHANNELS_QUERY_KEY })
    },
  })

  useEffect(() => {
    if (!successMessage) return
    const timer = window.setTimeout(() => setSuccessMessage(''), 4000)
    return () => window.clearTimeout(timer)
  }, [successMessage])

  const config = channelsQuery.data
  const disabled = saveMutation.isPending || channelsQuery.isFetching || channelsQuery.isError

  return <div className={styles.page}>
    <div className={styles.actions}>
      <AppButton
        icon={<RotateCcw size={16} />}
        variant="secondary"
        disabled={saveMutation.isPending || channelsQuery.isFetching}
        onClick={() => void channelsQuery.refetch()}
      >
        {channelsQuery.isFetching ? '刷新中' : '刷新'}
      </AppButton>
    </div>

    {channelsQuery.isPending && (
      <div className={styles.loadingState}>
        <div className={styles.spinner} />
        <p>加载认证渠道配置中…</p>
      </div>
    )}

    {channelsQuery.isError && (
      <div className={styles.errorState}>
        <AlertCircle size={48} />
        <h2>配置加载失败</h2>
        <p>{config ? '配置刷新失败，请重试后再修改' : '认证渠道配置加载失败，请重试'}</p>
        <AppButton
          variant="primary"
          disabled={channelsQuery.isFetching}
          onClick={() => void channelsQuery.refetch()}
        >
          重新加载
        </AppButton>
      </div>
    )}

    {config && (
      <div className={styles.channelsContainer}>
        <ChannelSettings
          channel="phone"
          saved={{ enabled: config.phoneEnabled, codeEnabled: config.phoneCodeEnabled }}
          otherEnabled={config.emailEnabled}
          disabled={disabled}
          saving={saveMutation.isPending && saveMutation.variables.channel === 'phone'}
          onSave={(update, onSuccess) => saveMutation.mutate({ channel: 'phone', update }, { onSuccess })}
        />
        <ChannelSettings
          channel="email"
          saved={{ enabled: config.emailEnabled, codeEnabled: config.emailCodeEnabled }}
          otherEnabled={config.phoneEnabled}
          disabled={disabled}
          saving={saveMutation.isPending && saveMutation.variables.channel === 'email'}
          onSave={(update, onSuccess) => saveMutation.mutate({ channel: 'email', update }, { onSuccess })}
        />
      </div>
    )}

    <Toast open={Boolean(successMessage)}>{successMessage}</Toast>
  </div>
}
