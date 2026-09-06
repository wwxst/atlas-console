import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, Download, MoreHorizontal, Plus } from 'lucide-react'
import { getDashboardSummary, getRecentActivity } from '@/features/dashboard/api'
import { AppButton, IconButton, Panel, ProgressBar, StatusBadge } from '@ui/index'
import styles from './DashboardPage.module.less'

export default function DashboardPage() {
  const summary = useQuery({ queryKey: ['dashboard-summary'], queryFn: getDashboardSummary })
  const activity = useQuery({ queryKey: ['dashboard-activity'], queryFn: getRecentActivity })
  if (summary.isLoading || activity.isLoading) return <div className={styles.loading}>正在加载工作台...</div>
  if (summary.isError || activity.isError || !summary.data || !activity.data) return <div className={styles.loading}>工作台数据加载失败，请稍后重试。</div>

  const metrics = [
    { label: '本月收入', value: `¥${summary.data.revenue.toLocaleString()}`, change: summary.data.revenueChange, note: '较上月' },
    { label: '活跃用户', value: summary.data.activeUsers.toLocaleString(), change: summary.data.activeUsersChange, note: '较上月' },
    { label: '转化率', value: `${summary.data.conversion}%`, change: summary.data.conversionChange, note: '较上月' },
    { label: '进行中的项目', value: summary.data.projects, change: 2, note: '本周新增' },
  ]

  return <div className={styles.page}>
    <div className={styles.pageIntro}>
      <div><h1>早上好，林晓</h1><p>这是今天的业务概览，祝你工作顺利。</p></div>
      <div className={styles.actions}><AppButton icon={<Download size={16} />}>导出报告</AppButton><AppButton variant="primary" icon={<Plus size={16} />}>新建项目</AppButton></div>
    </div>

    <div className={styles.metrics}>{metrics.map((metric) => <Panel key={metric.label} className={styles.metric}><span className={styles.secondary}>{metric.label}</span><div className={styles.metricValue}>{metric.value}</div><div className={styles.metricChange}><ArrowUpRight size={15} /> {metric.change}% <span>{metric.note}</span></div></Panel>)}</div>

    <div className={styles.mainGrid}>
      <Panel title="经营趋势" extra={<IconButton label="更多经营趋势操作"><MoreHorizontal size={18} /></IconButton>}>
        <div className={styles.chartHeader}><div><span className={styles.secondary}>总收入</span><strong>¥284,650</strong></div><StatusBadge tone="success">较上月 +12.6%</StatusBadge></div>
        <div className={styles.chart} aria-label="近六个月收入趋势"><div className={styles.chartLine}><span style={{ height: '42%' }} /><span style={{ height: '56%' }} /><span style={{ height: '48%' }} /><span style={{ height: '72%' }} /><span style={{ height: '66%' }} /><span style={{ height: '92%' }} /></div><div className={styles.chartLabels}><span>1 月</span><span>2 月</span><span>3 月</span><span>4 月</span><span>5 月</span><span>6 月</span></div></div>
      </Panel>
      <Panel title="项目进度" extra={<AppButton variant="ghost">查看全部</AppButton>}>
        <div className={styles.progressList}><div><div className={styles.progressLabel}><span>年度经营计划</span><strong>82%</strong></div><ProgressBar value={82} /></div><div><div className={styles.progressLabel}><span>客户体验升级</span><strong>64%</strong></div><ProgressBar value={64} color="var(--atlas-color-positive)" /></div><div><div className={styles.progressLabel}><span>数据资产治理</span><strong>48%</strong></div><ProgressBar value={48} color="var(--atlas-color-warning)" /></div></div>
      </Panel>
    </div>

    <Panel title="最近动态" extra={<AppButton variant="ghost">查看全部</AppButton>}>
      <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>事项</th><th>状态</th><th>时间</th></tr></thead><tbody>{activity.data.map((item) => <tr key={item.id}><td><strong>{item.title}</strong><span>{item.detail}</span></td><td><StatusBadge tone={item.status}>{item.status === 'success' ? '已完成' : item.status === 'warning' ? '需关注' : '处理中'}</StatusBadge></td><td>{item.time}</td></tr>)}</tbody></table></div>
    </Panel>
  </div>
}
