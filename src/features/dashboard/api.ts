export interface DashboardSummary { revenue: number; revenueChange: number; activeUsers: number; activeUsersChange: number; conversion: number; conversionChange: number; projects: number }
export interface ActivityItem { id: number; title: string; detail: string; time: string; status: 'success' | 'processing' | 'warning' }

export const getDashboardSummary = async (): Promise<DashboardSummary> => ({ revenue: 284650, revenueChange: 12.6, activeUsers: 18426, activeUsersChange: 8.4, conversion: 68.2, conversionChange: 4.8, projects: 24 })
export const getRecentActivity = async (): Promise<ActivityItem[]> => [
  { id: 1, title: '季度经营分析报告', detail: '李明完成了文档发布', time: '10 分钟前', status: 'success' },
  { id: 2, title: '供应商准入流程', detail: '审批节点等待处理', time: '36 分钟前', status: 'processing' },
  { id: 3, title: '华东区域数据同步', detail: '发现 2 条数据异常', time: '1 小时前', status: 'warning' },
  { id: 4, title: '客户满意度调研', detail: '本周问卷已收集 328 份', time: '2 小时前', status: 'success' },
]
