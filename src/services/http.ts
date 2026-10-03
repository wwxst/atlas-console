import axios from 'axios'
import { getApiErrorMessage } from './api.ts'
import { emitRequestError } from './requestErrorBus.ts'

export const ACCESS_TOKEN_KEY = 'atlas-access-token'
export const REFRESH_TOKEN_KEY = 'atlas-refresh-token'

const baseURL = import.meta.env?.VITE_API_BASE_URL ?? '/api'

export const http = axios.create({ baseURL, timeout: 10_000 })

/**
 * Refresh 请求使用独立客户端。
 * 它不注册任何拦截器，因此 Refresh 请求本身不会再次触发 Refresh。
 */
export const refreshClient = axios.create({ baseURL, timeout: 10_000 })

const authTokenEvents = new EventTarget()

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function saveAuthTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  authTokenEvents.dispatchEvent(new Event('change'))
}

export function clearAuthTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
  authTokenEvents.dispatchEvent(new Event('change'))
}

export function getAuthTokenSnapshot(): string {
  return `${getAccessToken() ?? ''}\n${getRefreshToken() ?? ''}`
}

export function subscribeAuthTokens(listener: () => void): () => void {
  authTokenEvents.addEventListener('change', listener)
  return () => authTokenEvents.removeEventListener('change', listener)
}

interface RefreshResponse {
  code: number
  msg: string | null
  data: { accessToken: string } | null
}

interface RefreshAttempt {
  refreshToken: string | null
  promise: Promise<string>
}

let refreshAttempt: RefreshAttempt | null = null

function isLoginRequest(url: string | undefined): boolean {
  return typeof url === 'string' && url.endsWith('/auth/login')
}

async function requestNewAccessToken(refreshToken: string): Promise<string> {
  const response = await refreshClient.post<RefreshResponse>(
    '/sys-user/auth/refresh',
    { refreshToken },
  )

  const data = response.data
  if (data?.code !== 200 || !data.data?.accessToken) {
    throw new Error(data?.msg ?? '刷新登录状态失败')
  }

  if (getRefreshToken() !== refreshToken) {
    throw new Error('登录状态已变更')
  }

  localStorage.setItem(ACCESS_TOKEN_KEY, data.data.accessToken)
  authTokenEvents.dispatchEvent(new Event('change'))
  return data.data.accessToken
}

/**
 * 同一时刻只发起一次 Refresh，其余请求复用同一个 Promise。
 */
function refreshAccessToken(): RefreshAttempt {
  if (!refreshAttempt) {
    const refreshToken = getRefreshToken()
    let attempt: RefreshAttempt
    const promise = (refreshToken
      ? requestNewAccessToken(refreshToken)
      : Promise.reject(new Error('缺少刷新令牌'))
    ).finally(() => {
      if (refreshAttempt === attempt) refreshAttempt = null
    })
    attempt = { refreshToken, promise }
    refreshAttempt = attempt
  }
  return refreshAttempt
}

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token && !isLoginRequest(config.url)) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as (typeof error.config & { __authRetried?: boolean }) | undefined
    const status = error.response?.status
    const isRefreshRequest = typeof config?.url === 'string' && config.url.includes('/auth/refresh')
    const isLogin = isLoginRequest(config?.url)

    // 普通请求遇到 401 时刷新一次并重试原请求一次；Refresh 请求自身不进入该流程
    if (status === 401 && config && !config.__authRetried && !isRefreshRequest && !isLogin) {
      config.__authRetried = true
      const attempt = refreshAccessToken()
      let accessToken: string

      try {
        accessToken = await attempt.promise
      } catch (refreshError) {
        if (getRefreshToken() === attempt.refreshToken) {
          clearAuthTokens()
          emitRequestError(getApiErrorMessage(refreshError, '登录状态已失效，请重新登录'))
        }
        return Promise.reject(error)
      }

      config.headers.set('Authorization', `Bearer ${accessToken}`)
      return http.request(config)
    }

    const authorization = config?.headers?.get('Authorization')
    const failedAccessToken = authorization?.startsWith('Bearer ')
      ? authorization.slice('Bearer '.length)
      : null
    if (status === 401 && failedAccessToken === getAccessToken()) clearAuthTokens()

    emitRequestError(getApiErrorMessage(error, '请求失败，请稍后重试'))
    return Promise.reject(error)
  },
)
