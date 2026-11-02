import { useSearchParams } from 'react-router-dom'
import type { KeyboardEvent } from 'react'
import PhoneLoginSettingsPage from './PhoneLoginSettingsPage'
import SmsTemplatesPage from './SmsTemplatesPage'
import MessageManagementPage from './MessageManagementPage'
import styles from './SmsMessageSettingsPage.module.less'

const tabs = [
  { key: 'provider', label: '短信服务商配置' },
  { key: 'templates', label: '短信模板管理' },
  { key: 'notifications', label: '消息通知管理' },
] as const

type PanelKey = typeof tabs[number]['key']

export default function SmsMessageSettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const panel = searchParams.get('panel')
  const activePanel: PanelKey = panel === 'templates' || panel === 'notifications' ? panel : 'provider'

  const selectPanel = (key: PanelKey) => {
    const next = new URLSearchParams(searchParams)
    if (key === 'provider') next.delete('panel')
    else next.set('panel', key)
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
    const nextTab = tabs[nextIndex].key
    selectPanel(nextTab)
    document.getElementById('sms-message-tab-' + nextTab)?.focus()
  }

  return <div className={styles.page}>
    <header className={styles.header}>
      <h1>短信与消息配置</h1>
      <p>统一管理短信服务商、短信模板及业务消息通知，配置内容通过标签页切换。</p>
    </header>

    <section className={styles.tabSection} aria-label="短信与消息配置">
      <div className={styles.tabs} role="tablist" aria-label="短信与消息配置分类">
        {tabs.map((tab, index) => <button
          key={tab.key}
          id={'sms-message-tab-' + tab.key}
          type="button"
          role="tab"
          aria-selected={activePanel === tab.key}
          aria-controls={'sms-message-panel-' + tab.key}
          tabIndex={activePanel === tab.key ? 0 : -1}
          className={[styles.tab, activePanel === tab.key ? styles.tabActive : ''].join(' ')}
          onClick={() => selectPanel(tab.key)}
          onKeyDown={(event) => handleTabKey(event, index)}
        >{tab.label}</button>)}
      </div>
    </section>

    <section id="sms-message-panel-provider" role="tabpanel" aria-labelledby="sms-message-tab-provider" tabIndex={0} hidden={activePanel !== 'provider'}>
      <PhoneLoginSettingsPage active={activePanel === 'provider'} />
    </section>
    <section id="sms-message-panel-templates" role="tabpanel" aria-labelledby="sms-message-tab-templates" tabIndex={0} hidden={activePanel !== 'templates'}>
      <SmsTemplatesPage active={activePanel === 'templates'} />
    </section>
    <section id="sms-message-panel-notifications" role="tabpanel" aria-labelledby="sms-message-tab-notifications" tabIndex={0} hidden={activePanel !== 'notifications'}>
      <MessageManagementPage active={activePanel === 'notifications'} />
    </section>
  </div>
}
