import { X } from 'lucide-react'
import { useState } from 'react'
import type { KeyboardEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AppButton, IconButton } from '@ui/index'
import { StorageProviderPanel } from './StorageProviderPanel'
import styles from './StorageSettingsPage.module.less'

const providers = [
  { key: 'qiniu', label: '七牛云存储', accessLabel: 'AccessKey', secretLabel: 'SecretKey' },
  { key: 'aliyun', label: '阿里云存储', accessLabel: 'AccessKeyId', secretLabel: 'AccessKeySecret' },
  { key: 'tencent', label: '腾讯云存储', accessLabel: 'SecretId', secretLabel: 'SecretKey' },
  { key: 'jd', label: '京东云存储', accessLabel: 'AccessKey', secretLabel: 'SecretKey' },
  { key: 'huawei', label: '华为云存储', accessLabel: 'Access Key ID', secretLabel: 'Secret Access Key' },
  { key: 'tianyi', label: '天翼云存储', accessLabel: 'AccessKey', secretLabel: 'SecretKey' },
] as const

type ProviderKey = typeof providers[number]['key']
type TabKey = 'general' | ProviderKey
const tabs = [{ key: 'general', label: '存储配置' }, ...providers] as const

const providerWebsites: Record<ProviderKey, string> = {
  qiniu: 'https://www.qiniu.com', aliyun: 'https://www.aliyun.com', tencent: 'https://cloud.tencent.com',
  jd: 'https://www.jdcloud.com', huawei: 'https://www.huaweicloud.com', tianyi: 'https://www.ctyun.cn',
}

function SettingSwitch({ label, checked, onChange }: { label: string; checked: boolean; onChange: () => void }) {
  return <div className={styles.settingRow}>
    <span className={styles.rowLabel}>{label}：</span>
    <button type="button" role="switch" aria-label={label} aria-checked={checked} className={[styles.toggle, checked ? styles.toggleOn : ''].join(' ')} onClick={onChange}><span /></button>
  </div>
}

export default function StorageSettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedTab = searchParams.get('tab')
  const activeTab: TabKey = providers.find(({ key }) => key === requestedTab)?.key ?? 'general'
  const provider = providers.find(({ key }) => key === activeTab)
  const [storageType, setStorageType] = useState<'local' | ProviderKey>('local')
  const [thumbnailEnabled, setThumbnailEnabled] = useState(false)
  const [watermarkEnabled, setWatermarkEnabled] = useState(false)
  const [thumbnailSizes, setThumbnailSizes] = useState(['800', '300', '150'])
  const [watermarkText, setWatermarkText] = useState('')
  const [watermarkPosition, setWatermarkPosition] = useState('bottom-right')
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
      {!hiddenNotices[activeTab] && <div className={styles.notice}>
        {provider ? <div>
          <p>{provider.label}开通方法：<a href={providerWebsites[provider.key]} target="_blank" rel="noreferrer">查看官网</a></p>
          <p>第一步：添加存储空间，空间名称不能重复。</p>
          <p>第二步：开启存储空间的使用状态。</p>
          <p>第三步（可选）：修改空间域名，并在域名服务商完成解析配置。</p>
        </div> : <div><p>缩略图默认尺寸：大图 800 × 800、中图 300 × 300、小图 150 × 150。</p><p>水印设置用于后续上传的图片，已上传的图片不追溯处理。</p></div>}
        <IconButton label="关闭存储提示" className={styles.closeNotice} onClick={() => setHiddenNotices((hidden) => ({ ...hidden, [activeTab]: true }))}><X size={15} /></IconButton>
      </div>}
    </section>

    <form hidden={activeTab !== 'general'} id="storage-panel-general" role="tabpanel" aria-labelledby="storage-tab-general" className={styles.configSection} onSubmit={(event) => event.preventDefault()}>
        <fieldset className={styles.storageOptions}>
          <legend>存储方式：</legend>
          <div className={styles.radios}>
            {[{ key: 'local', label: '本地存储' }, ...providers].map((option) => <label key={option.key}><input type="radio" name="storageType" value={option.key} checked={storageType === option.key} onChange={() => setStorageType(option.key as 'local' | ProviderKey)} /><span>{option.label}</span></label>)}
          </div>
        </fieldset>

        <div className={styles.settingSection}>
          <SettingSwitch label="是否开启缩略图" checked={thumbnailEnabled} onChange={() => setThumbnailEnabled((enabled) => !enabled)} />
          {thumbnailEnabled && <div className={styles.extraFields}>
            {['大图尺寸', '中图尺寸', '小图尺寸'].map((label, index) => <div key={label} className={styles.sizeField}><label htmlFor={`thumbnail-size-${index}`}>{label}</label><div><input id={`thumbnail-size-${index}`} type="number" min="1" step="1" value={thumbnailSizes[index]} onChange={(event) => setThumbnailSizes((sizes) => sizes.map((size, i) => i === index ? event.target.value : size))} /><span>×</span><span>{thumbnailSizes[index] || '—'}</span><small>px</small></div></div>)}
          </div>}
        </div>

        <div className={styles.settingSection}>
          <SettingSwitch label="是否开启水印" checked={watermarkEnabled} onChange={() => setWatermarkEnabled((enabled) => !enabled)} />
          {watermarkEnabled && <div className={styles.extraFields}>
            <div className={styles.field}><label htmlFor="watermark-text">水印文字</label><input id="watermark-text" value={watermarkText} onChange={(event) => setWatermarkText(event.target.value)} placeholder="请输入水印文字" maxLength={50} /></div>
            <div className={styles.field}><label htmlFor="watermark-position">水印位置</label><select id="watermark-position" value={watermarkPosition} onChange={(event) => setWatermarkPosition(event.target.value)}><option value="bottom-right">右下角</option><option value="bottom-left">左下角</option><option value="top-right">右上角</option><option value="top-left">左上角</option><option value="center">居中</option></select></div>
          </div>}
        </div>
      <div className={styles.actions}>
        <AppButton type="submit" variant="primary" disabled aria-describedby="storage-save-help">保存</AppButton>
        <p id="storage-save-help">当前为页面预览，保存功能待开放。</p>
      </div>
    </form>
    {providers.map((item) => <div key={item.key} hidden={activeTab !== item.key} id={`storage-panel-${item.key}`} role="tabpanel" aria-labelledby={`storage-tab-${item.key}`}><StorageProviderPanel provider={item} /></div>)}
  </div>
}
