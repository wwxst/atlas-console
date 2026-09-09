import axios from 'axios'

export interface ApiResult<T> {
  code: number
  msg: string | null
  data: T | null
}

export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  records: T[]
}

export function requireApiData<T>(result: ApiResult<T>): T {
  if (result.code !== 200 || result.data === null) {
    throw new Error(result.msg ?? '请求失败，请稍后重试')
  }

  return result.data
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiResult<unknown>>(error)) {
    const message = error.response?.data?.msg
    if (typeof message === 'string' && message.trim()) return message
  }

  if (error instanceof Error && error.message.trim()) return error.message
  return fallback
}
