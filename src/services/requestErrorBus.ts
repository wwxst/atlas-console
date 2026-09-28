export type RequestErrorListener = (message: string, id: number) => void

const DEDUPE_WINDOW_MS = 4_000
const listeners = new Set<RequestErrorListener>()
const recentMessages = new Map<string, number>()
let nextNotificationId = 0

export function subscribeRequestErrors(listener: RequestErrorListener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function emitRequestError(message: string): void {
  const now = Date.now()
  const lastEmittedAt = recentMessages.get(message)
  if (lastEmittedAt !== undefined && now - lastEmittedAt < DEDUPE_WINDOW_MS) return

  recentMessages.set(message, now)
  for (const listener of listeners) listener(message, ++nextNotificationId)
}
