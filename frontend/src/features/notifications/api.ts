export interface NotificationItem {
  id: number
  title: string
  time: string
  unread: boolean
}

export const getNotifications = async (): Promise<NotificationItem[]> => [
  { id: 1, title: '供应商准入流程等待审批', time: '36 分钟前', unread: true },
  { id: 2, title: '华东区域数据同步完成', time: '1 小时前', unread: true },
  { id: 3, title: '季度经营报告已发布', time: '昨天', unread: false },
]
