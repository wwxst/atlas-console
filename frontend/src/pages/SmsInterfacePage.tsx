import { useSearchParams } from 'react-router-dom'
import type { KeyboardEvent } from 'react'
import PhoneLoginSettingsPage from './PhoneLoginSettingsPage'
import SmsTemplatesPage from './SmsTemplatesPage'
import styles from './SmsMessageSettingsPage.module.less'

const tabs = [
  { key: 'provider', label: '短信服务商配置' },
  { key: 'templates', label: '短信模板管理' },
] as const

type PanelKey = typeof tabs[number]['key']

export default function SmsInterfacePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const panel = searchParams.get('panel')
  const activePanel: PanelKey = panel === 'templates' ? 'templates' : 'provider'

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
    document.getElementById('sms-interface-tab-' + nextTab)?.focus()
  }

  return <div className={styles.page}>
    <header className={styles.header}>
      <h1>短信接口</h1>
      <p>配置短信服务商接入信息，并统一登记和管理各平台短信模板。</p>
    </header>

    <section className={styles.tabSection} aria-label="短信接口">
      <div className={styles.tabs} role="tablist" aria-label="短信接口配置分类">
        {tabs.map((tab, index) => <button
          key={tab.key}
          id={'sms-interface-tab-' + tab.key}
          type="button"
          role="tab"
          aria-selected={activePanel === tab.key}
          aria-controls={'sms-interface-panel-' + tab.key}
          tabIndex={activePanel === tab.key ? 0 : -1}
          className={[styles.tab, activePanel === tab.key ? styles.tabActive : ''].join(' ')}
          onClick={() => selectPanel(tab.key)}
          onKeyDown={(event) => handleTabKey(event, index)}
        >{tab.label}</button>)}
      </div>
    </section>

    <section id="sms-interface-panel-provider" role="tabpanel" aria-labelledby="sms-interface-tab-provider" tabIndex={0} hidden={activePanel !== 'provider'}>
      <PhoneLoginSettingsPage active={activePanel === 'provider'} />
    </section>
    <section id="sms-interface-panel-templates" role="tabpanel" aria-labelledby="sms-interface-tab-templates" tabIndex={0} hidden={activePanel !== 'templates'}>
      <SmsTemplatesPage active={activePanel === 'templates'} />
    </section>
  </div>
}
