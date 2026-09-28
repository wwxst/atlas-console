import { useEffect, useState } from 'react'
import { subscribeRequestErrors } from '@/services/requestErrorBus'
import { Toast } from './Toast'

interface RequestErrorNotification {
  id: number
  message: string
}

export function RequestErrorToast() {
  const [notification, setNotification] = useState<RequestErrorNotification | null>(null)

  useEffect(() => subscribeRequestErrors((message, id) => setNotification({ message, id })), [])

  useEffect(() => {
    if (!notification) return

    const timer = window.setTimeout(() => setNotification(null), 4_000)
    return () => window.clearTimeout(timer)
  }, [notification])

  return <Toast key={notification?.id} open={Boolean(notification)} tone="danger">{notification?.message}</Toast>
}
