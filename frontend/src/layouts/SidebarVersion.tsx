import { useId, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowUpRight, Clock3, FileText, Package, RefreshCw, RotateCcw, Rocket, X } from 'lucide-react'
import { CURRENT_SYS_USER_QUERY_KEY, getCurrentSysUser } from '@/features/auth/api'
import { getDeploymentStatus, getReleaseHistory, rollbackDeployment, rollbackToHistory, startDeployment } from '@/features/deployment/api'
import type { ReleaseHistoryItem } from '@/features/deployment/api'
import { getApiErrorMessage } from '@/services/api'
import { IconButton } from '@ui/index'
import { version } from '../../package.json'
import styles from './SidebarVersion.module.less'

const activeStates = new Set(['requested', 'queued', 'in_progress', 'waiting', 'pending'])

function description(state: string, conclusion: string | null, updateAvailable: boolean): string {
  if (state === 'not_configured') return '本机更新执行器尚未初始化'
  if (state === 'no_release') return 'GitHub 尚未发布正式版本'
  if (state === 'release_unavailable') return '暂时无法获取 GitHub 版本信息'
  if (activeStates.has(state)) return '正在下载正式版本、构建镜像并检查服务健康状态'
  if (state === 'completed' && conclusion === 'success') return '最近一次更新已成功'
  if (state === 'completed' && conclusion === 'failure') return '最近一次更新失败，请查看服务器更新日志'
  if (updateAvailable) return '发现可用的新版本'
  return '当前已是最新正式版本'
}

export default function SidebarVersion({ collapsed }: { collapsed: boolean }) {
  const queryClient = useQueryClient()
  const panelId = useId()
  const titleId = useId()
  const [open, setOpen] = useState(false)

  const currentUser = useQuery({
    queryKey: CURRENT_SYS_USER_QUERY_KEY,
    queryFn: getCurrentSysUser,
    retry: false,
  })
  const isSuperAdmin = currentUser.data?.id === 1
  const deployment = useQuery({
    queryKey: ['deployment-status'],
    queryFn: getDeploymentStatus,
    enabled: open && isSuperAdmin,
    refetchInterval: open && isSuperAdmin ? 10_000 : false,
    retry: false,
  })
  const history = useQuery({
    queryKey: ['deployment-history'],
    queryFn: getReleaseHistory,
    enabled: open && isSuperAdmin && deployment.data?.configured === true,
    refetchInterval: open && isSuperAdmin ? 30_000 : false,
    retry: false,
  })
  const update = useMutation({
    mutationFn: startDeployment,
    onSuccess: (result) => {
      queryClient.setQueryData(['deployment-status'], result)
      void deployment.refetch()
      void history.refetch()
    },
  })
  const rollback = useMutation({
    mutationFn: rollbackDeployment,
    onSuccess: (result) => {
      queryClient.setQueryData(['deployment-status'], result)
      void deployment.refetch()
    },
  })

  const restore = useMutation({
    mutationFn: rollbackToHistory,
    onSuccess: (result) => {
      queryClient.setQueryData(['deployment-status'], result)
      void deployment.refetch()
      void history.refetch()
    },
  })

  const status = deployment.data
  const nativeMode = status?.deploymentMode === 'baota'
  const busy = Boolean(status && activeStates.has(status.state)) ||
    update.isPending || rollback.isPending || restore.isPending
  const controlsBlocked = busy || deployment.isError
  const runLink = status?.latestReleaseUrl?.startsWith('https://github.com/wwxst/atlas-console/releases/')
    ? status.latestReleaseUrl : null

  const runRollback = () => {
    if (!window.confirm(`确定回滚到上一版本吗？这将恢复上一套${nativeMode ? '宝塔静态页面和 Java JAR' : '前后端容器镜像'}，但不会恢复数据库或上传文件。`)) return
    rollback.mutate()
  }

  const runRestore = (release: ReleaseHistoryItem) => {
    if (release.current || controlsBlocked) return
    if (!window.confirm(`确认回滚到历史版本 ${release.version}？这只会恢复${nativeMode ? '宝塔静态页面和 JAR' : '应用容器'}，不会回滚数据库结构或恢复上传文件。`)) return
    restore.mutate(release.releaseId)
  }

  const runUpdate = () => {
    const versionLabel = status?.latestVersion ? `v${status.latestVersion}` : '最新正式版本'
    if (!window.confirm(`将从 GitHub Releases 下载 ${versionLabel}，在本机服务器${nativeMode ? '校验发布资产、切换宝塔静态页面并重启 Java JAR' : '构建并更新容器'}。更新期间服务可能短暂中断，数据库迁移不会随应用回滚。确定开始吗？`)) return
    update.mutate()
  }

  return <>
    <button
      type="button"
      className={styles.trigger}
      aria-label="版本与更新"
      aria-haspopup="dialog"
      aria-expanded={open}
      aria-controls={panelId}
      popoverTarget={panelId}
      title={collapsed ? `版本与更新 · v${version}` : undefined}
    >
      <Package size={20} strokeWidth={1.5} aria-hidden="true" />
      {!collapsed && <span className={styles.version}>v{version}</span>}
    </button>
    <section
      id={panelId}
      popover="auto"
      role="dialog"
      aria-labelledby={titleId}
      className={[styles.panel, collapsed && styles.panelCollapsed].filter(Boolean).join(' ')}
      onToggle={(event) => {
        const nextOpen = event.newState === 'open'
        setOpen(nextOpen)
        if (nextOpen) event.currentTarget.querySelector('button')?.focus()
      }}
    >
      <header className={styles.header}>
        <h2 id={titleId}>版本与更新</h2>
        <IconButton label="关闭版本面板" popoverTarget={panelId} popoverTargetAction="hide"><X size={15} aria-hidden="true" /></IconButton>
      </header>
      <div className={styles.summary}>
        <span className={styles.packageIcon}><Package size={23} strokeWidth={1.5} aria-hidden="true" /></span>
        <strong>v{version}</strong>
        <span>当前前端版本 · Atlas Console</span>
      </div>
      <div className={styles.deployment}>
        {!isSuperAdmin ? <p className={styles.notice}>仅超级管理员可以查看和触发服务器更新。</p>
          : <>
            <p className={styles.notice} role="status">
              {deployment.isPending ? '正在查询部署状态…'
                : deployment.isError ? '暂时无法连接到本机更新执行器'
                  : description(status?.state ?? 'unknown', status?.conclusion ?? null, Boolean(status?.updateAvailable))}
            </p>
            {status?.configured && <>
              <p className={styles.explanation}>当前部署方式：{nativeMode ? '宝塔原生（Nginx + Java JAR）' : 'Docker Compose'}</p>
              <p className={styles.explanation}>{nativeMode
                ? '从公开 GitHub Releases 下载校验过的前端静态包与 Java JAR，原子切换应用版本并重启 Java 服务；无需 Docker、GitHub PAT 或 Runner。'
                : '直接检查公开的 GitHub Releases。更新时由本机隔离执行器下载正式版本、构建并替换容器；客户无需 GitHub 账号、PAT 或 Actions Runner。'}</p>
              <p className={styles.explanation}>当前版本：{status?.currentVersion ? `v${status.currentVersion}` : '未记录'} · 最新版本：{status?.latestVersion ? `v${status.latestVersion}` : '暂无'}</p>
              {status?.requestId && <p className={styles.explanation}>更新请求：{status.requestId}</p>}
              {status?.message && <p className={styles.explanation}>{status.message}</p>}
              {status?.releaseCheckError && <p className={styles.explanation}>{status.releaseCheckError}</p>}
              {status?.releaseNotes && status.updateAvailable && <details className={styles.releaseNotes}>
                <summary>查看 v{status.latestVersion} 更新内容</summary>
                <p>{status.releaseNotes}</p>
              </details>}
              {runLink && <a className={styles.runLink} href={runLink} target="_blank" rel="noopener noreferrer">查看 GitHub 发布说明 <ArrowUpRight size={13} /></a>}
            </>}
            {!status?.configured && !deployment.isPending && !deployment.isError
              && <p className={styles.explanation}>请使用官方安装器完成首次安装。安装器会自动配置本机更新执行器和独立访问密钥，无需配置 GitHub PAT 或 Runner。</p>}
            {deployment.isError && <button type="button" className={styles.action} onClick={() => void deployment.refetch()}><RefreshCw size={15} />重试查询</button>}
            <button type="button" className={styles.action}
              disabled={!status?.configured || !status?.updateAvailable || controlsBlocked}
              aria-busy={update.isPending} onClick={runUpdate}>
              <Rocket size={16} />{update.isPending ? '正在启动…' : busy ? '更新执行中' : status?.updateAvailable ? `一键更新至 v${status.latestVersion}` : '已是最新版本'}
            </button>
            {status?.configured && <>
              <button type="button" className={styles.actionSecondary}
                disabled={!status.rollbackAvailable || controlsBlocked} onClick={runRollback}>
                <RotateCcw size={15} />{rollback.isPending ? '正在提交回滚…' : '回滚上一版本'}
              </button>
              {!status.rollbackAvailable && <p className={styles.explanation}>没有可回滚的上一版本。至少成功部署两次后才可使用。</p>}
            </>}
            {status?.configured && <section className={styles.history} aria-label="历史版本">
              <div className={styles.historyHeading}>
                <span><Clock3 size={15} aria-hidden="true" />历史版本</span>
                <button type="button" className={styles.historyRefresh} onClick={() => void history.refetch()}
                  disabled={history.isFetching || busy}>刷新</button>
              </div>
              {history.isPending && <p className={styles.explanation}>正在加载历史版本…</p>}
              {history.isError && <p role="alert" className={styles.error}>读取历史版本失败，请重试。</p>}
              {history.isSuccess && history.data.length === 0 &&
                <p className={styles.explanation}>暂无部署记录，首次上线后将开始记录历史版本。</p>}
              {history.data?.length ? <div className={styles.historyList}>
                {history.data.map((release) => <div key={release.releaseId} className={styles.historyItem}>
                  <div className={styles.historyInfo}>
                    <strong>{release.current ? `当前版本 · ${release.version}` : release.version}</strong>
                    <small>前后端提交 {release.frontendSha.slice(0, 8)}</small>
                    <small>{new Date(release.deployedAt).toLocaleString('zh-CN')}</small>
                  </div>
                  <button type="button" className={styles.historyRestore}
                    disabled={release.current || controlsBlocked} onClick={() => runRestore(release)}
                    title={release.current ? '当前正在运行' : `回滚到版本 ${release.releaseId}`}>
                    {release.current ? '当前' : '回滚到此版本'}
                  </button>
                </div>)}
              </div> : null}
              <p className={styles.explanation}>仅显示最近 50 条已记录的应用版本。恢复需要服务器仍保留对应${nativeMode ? '本地发布文件' : '镜像'}。</p>
            </section>}
            {restore.isError && <p role="alert" className={styles.error}>{getApiErrorMessage(restore.error, '历史版本回滚提交失败')}</p>}
            {update.isError && <p role="alert" className={styles.error}>{getApiErrorMessage(update.error, '提交更新失败')}</p>}
            {rollback.isError && <p role="alert" className={styles.error}>{getApiErrorMessage(rollback.error, '回滚提交失败')}</p>}
            {status?.state === 'completed' && status?.conclusion === 'success' && !busy &&
              <button type="button" className={styles.actionSecondary} onClick={() => window.location.reload()}>
                <RefreshCw size={15} />刷新以加载部署后的前端
              </button>}
          </>}
      </div>
      <a className={styles.releaseLink} href="https://github.com/wwxst/atlas-console/releases" target="_blank" rel="noopener noreferrer">
        <FileText size={16} aria-hidden="true" />
        <span>查看发布与更新日志</span>
        <ArrowUpRight size={15} aria-hidden="true" />
      </a>
    </section>
  </>
}
