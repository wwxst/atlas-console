import { useId, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowUpRight, FileText, Package, RefreshCw, Rocket, X } from 'lucide-react'
import { CURRENT_SYS_USER_QUERY_KEY, getCurrentSysUser } from '@/features/auth/api'
import { getDeploymentStatus, startDeployment } from '@/features/deployment/api'
import { getApiErrorMessage } from '@/services/api'
import { IconButton } from '@ui/index'
import { version } from '../../package.json'
import styles from './SidebarVersion.module.less'

const activeStates = new Set(['requested', 'queued', 'in_progress', 'waiting', 'pending'])

function description(state: string, conclusion: string | null): string {
  if (state === 'not_configured') return '尚未配置生产服务器，暂不能执行更新'
  if (state === 'not_started') return '尚无部署记录'
  if (activeStates.has(state)) return '正在等待或执行 GitHub Actions 部署'
  if (conclusion === 'success') return '最近一次部署已成功'
  if (conclusion === 'failure') return '最近一次部署失败，请查看日志'
  if (conclusion === 'cancelled') return '最近一次部署已取消'
  return '可查看最近一次部署状态'
}

export default function SidebarVersion({ collapsed }: { collapsed: boolean }) {
  const panelId = useId()
  const titleId = useId()
  const [open, setOpen] = useState(false)
  const [requestedId, setRequestedId] = useState<string | null>(null)

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
  const update = useMutation({
    mutationFn: startDeployment,
    onSuccess: (result) => {
      setRequestedId(result.requestId)
      void deployment.refetch()
    },
  })

  const status = deployment.data
  const awaitingRun = Boolean(requestedId && !status?.title?.includes(requestedId))
  const busy = awaitingRun || Boolean(status && activeStates.has(status.state))
  const runLink = status?.runUrl?.startsWith('https://github.com/wwxst/atlas-console/actions/')
    ? status.runUrl : null

  const runUpdate = () => {
    if (!window.confirm('将从 GitHub main 分支拉取前后端代码，并部署到正式服务器。更新期间服务可能短暂中断，确定开始吗？')) return
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
                : deployment.isError ? '暂时无法连接到更新服务'
                  : awaitingRun ? '更新请求已发出，等待 GitHub 创建任务…'
                    : description(status?.state ?? 'unknown', status?.conclusion ?? null)}
            </p>
            {status?.configured && <>
              <p className={styles.explanation}>从 GitHub 获取前后端 main 分支，经过验证后由服务器上的专用部署执行器更新容器。</p>
              {runLink && <a className={styles.runLink} href={runLink} target="_blank" rel="noopener noreferrer">查看执行记录 <ArrowUpRight size={13} /></a>}
            </>}
            {!status?.configured && !deployment.isPending && !deployment.isError
              && <p className={styles.explanation}>首次使用需先准备服务器、Docker 和 GitHub 自托管 Runner，再由管理员配置后端密钥。</p>}
            {deployment.isError && <button type="button" className={styles.action} onClick={() => void deployment.refetch()}><RefreshCw size={15} />重试查询</button>}
            <button type="button" className={styles.action} disabled={!status?.configured || busy || update.isPending}
              aria-busy={update.isPending} onClick={runUpdate}>
              <Rocket size={16} />{update.isPending ? '正在提交…' : busy ? '更新执行中' : '一键自动更新'}
            </button>
            {update.isError && <p role="alert" className={styles.error}>{getApiErrorMessage(update.error, '提交更新失败')}</p>}
            {status?.conclusion === 'success' && !busy &&
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
