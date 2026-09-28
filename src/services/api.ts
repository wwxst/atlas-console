import axios from 'axios'
import { emitRequestError } from './requestErrorBus.ts'

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
    const error = new Error(result.msg ?? '请求失败，请稍后重试')
    emitRequestError(getApiErrorMessage(error, '请求失败，请稍后重试'))
    throw error
  }

  return result.data
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiResult<unknown>>(error)) {
    const message = error.response?.data?.msg
    const status = error.response?.status
    if (typeof message === 'string' && /[\u3400-\u9fff]/u.test(message)) return message

    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT' || error.message.toLowerCase().includes('timeout')) {
      return '请求超时，请稍后重试'
    }

    if (status === 401) return '登录状态已失效，请重新登录'
    if (status === 403) return '没有权限执行此操作'
    if (status === 404) return '请求的资源不存在'
    if (status === 408) return '请求超时，请稍后重试'
    if (status === 429) return '请求过于频繁，请稍后重试'
    if (status !== undefined && status >= 500) return '服务暂时不可用，请稍后重试'
    if (!error.response) return '网络异常，请检查连接后重试'

    return fallback
  }

  if (error instanceof Error && /[\u3400-\u9fff]/u.test(error.message)) return error.message
  return fallback
}
