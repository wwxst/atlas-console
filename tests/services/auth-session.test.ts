import assert from 'node:assert/strict'
import test from 'node:test'
import { AxiosError } from 'axios'

class MemoryStorage {
  private store = new Map<string, string>()
  getItem(key: string): string | null {
    return this.store.has(key) ? (this.store.get(key) as string) : null
  }
  setItem(key: string, value: string): void {
    this.store.set(key, String(value))
  }
  removeItem(key: string): void {
    this.store.delete(key)
  }
  clear(): void {
    this.store.clear()
  }
}

// http.ts 使用 localStorage，Node 环境先注入内存实现，再动态导入被测模块
;(globalThis as unknown as { localStorage: MemoryStorage }).localStorage = new MemoryStorage()

const {
  http,
  refreshClient,
  ACCESS_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  getAccessToken,
  getRefreshToken,
  saveAuthTokens,
  clearAuthTokens,
  getAuthTokenSnapshot,
  subscribeAuthTokens,
} = await import('../../src/services/http.ts')

interface Outcome {
  status: number
  data: unknown
}

function headerOf(config: { headers?: unknown }, name: string): string | undefined {
  const headers = config?.headers as { get?: (key: string) => string | null } & Record<string, string> | undefined
  if (!headers) return undefined
  if (typeof headers.get === 'function') return headers.get(name) ?? undefined
  return headers[name] ?? headers[name.toLowerCase()]
}

function installAdapter(
  instance: { defaults: { adapter?: unknown } },
  handler: (config: never) => Outcome | Promise<Outcome>,
): void {
  instance.defaults.adapter = async (config: never) => {
    const outcome = await handler(config)
    const response = {
      data: outcome.data,
      status: outcome.status,
      statusText: String(outcome.status),
      headers: {},
      config,
      request: {},
    }
    if (outcome.status >= 200 && outcome.status < 300) return response
    throw new AxiosError(
      `Request failed with status code ${outcome.status}`,
      'ERR_BAD_RESPONSE',
      config,
      {},
      response,
    )
  }
}

function ok(data: unknown): Outcome {
  return { status: 200, data: { code: 200, msg: null, data } }
}

function unauthorized(): Outcome {
  return { status: 401, data: { code: 40102, msg: '登录状态无效或已过期', data: null } }
}

function serverError(): Outcome {
  return { status: 500, data: { code: 500, msg: '服务器错误', data: null } }
}

test('stores and clears both tokens', () => {
  let changes = 0
  const unsubscribe = subscribeAuthTokens(() => changes += 1)
  saveAuthTokens('access-a', 'refresh-a')
  assert.equal(getAccessToken(), 'access-a')
  assert.equal(getRefreshToken(), 'refresh-a')
  assert.equal(localStorage.getItem(ACCESS_TOKEN_KEY), 'access-a')
  assert.equal(localStorage.getItem(REFRESH_TOKEN_KEY), 'refresh-a')

  clearAuthTokens()
  assert.equal(getAccessToken(), null)
  assert.equal(getRefreshToken(), null)
  assert.equal(getAuthTokenSnapshot(), '\n')
  assert.equal(changes, 2)
  unsubscribe()
})

test('attaches the access token to outgoing requests', async () => {
  clearAuthTokens()
  saveAuthTokens('access-1', 'refresh-1')

  const seen: (string | undefined)[] = []
  installAdapter(http, (config) => {
    seen.push(headerOf(config, 'Authorization'))
    return ok({})
  })

  await http.get('/sys-user/sys-users')
  assert.deepEqual(seen, ['Bearer access-1'])
  clearAuthTokens()
})

test('does not attach tokens or refresh when login returns 401', async () => {
  clearAuthTokens()
  saveAuthTokens('existing-access', 'existing-refresh')

  let refreshCount = 0
  installAdapter(refreshClient, () => {
    refreshCount += 1
    return ok({ accessToken: 'refreshed-access' })
  })

  let loginAuthorization: string | undefined
  installAdapter(http, (config) => {
    loginAuthorization = headerOf(config, 'Authorization')
    return unauthorized()
  })

  await assert.rejects(() => http.post('/sys-user/auth/login', {
    username: 'admin',
    password: 'wrong-password',
  }))

  assert.equal(loginAuthorization, undefined)
  assert.equal(refreshCount, 0)
  assert.equal(getAccessToken(), 'existing-access')
  assert.equal(getRefreshToken(), 'existing-refresh')
  clearAuthTokens()
})

test('refreshes on 401 and retries the original request once', async () => {
  clearAuthTokens()
  saveAuthTokens('old-access', 'refresh-1')

  const refreshUrls: string[] = []
  installAdapter(refreshClient, (config) => {
    refreshUrls.push(String((config as { url?: string }).url))
    return ok({ accessToken: 'new-access' })
  })

  const resourceAuth: (string | undefined)[] = []
  installAdapter(http, (config) => {
    resourceAuth.push(headerOf(config, 'Authorization'))
    return resourceAuth.length === 1 ? unauthorized() : ok({ records: [] })
  })

  const response = await http.get('/sys-user/sys-users')

  assert.equal(response.status, 200)
  assert.equal(refreshUrls.length, 1)
  assert.ok(refreshUrls[0].includes('/auth/refresh'))
  assert.deepEqual(resourceAuth, ['Bearer old-access', 'Bearer new-access'])
  assert.equal(getAccessToken(), 'new-access')
  clearAuthTokens()
})

test('keeps refreshed tokens when the retried request fails with a server error', async () => {
  clearAuthTokens()
  saveAuthTokens('old-access', 'refresh-1')

  installAdapter(refreshClient, () => ok({ accessToken: 'new-access' }))
  let resourceCount = 0
  installAdapter(http, () => {
    resourceCount += 1
    return resourceCount === 1 ? unauthorized() : serverError()
  })

  await assert.rejects(() => http.get('/sys-user/sys-users'))

  assert.equal(resourceCount, 2)
  assert.equal(getAccessToken(), 'new-access')
  assert.equal(getRefreshToken(), 'refresh-1')
  clearAuthTokens()
})

test('an old refresh response cannot overwrite a newer login', async () => {
  clearAuthTokens()
  saveAuthTokens('old-access', 'old-refresh')

  let finishRefresh: ((outcome: Outcome) => void) | undefined
  installAdapter(refreshClient, () => new Promise<Outcome>((resolve) => {
    finishRefresh = resolve
  }))
  installAdapter(http, () => unauthorized())

  const request = http.get('/sys-user/sys-users')
  await new Promise((resolve) => setTimeout(resolve, 0))
  saveAuthTokens('new-login-access', 'new-login-refresh')
  finishRefresh?.(ok({ accessToken: 'stale-refresh-access' }))

  await assert.rejects(() => request)
  assert.equal(getAccessToken(), 'new-login-access')
  assert.equal(getRefreshToken(), 'new-login-refresh')
  clearAuthTokens()
})

test('retries at most once and clears tokens when the retry also fails', async () => {
  clearAuthTokens()
  saveAuthTokens('old-access', 'refresh-1')

  let refreshCount = 0
  installAdapter(refreshClient, () => {
    refreshCount += 1
    return ok({ accessToken: 'new-access' })
  })

  let resourceCount = 0
  installAdapter(http, () => {
    resourceCount += 1
    return unauthorized()
  })

  await assert.rejects(() => http.get('/sys-user/sys-users'))

  assert.equal(refreshCount, 1)
  assert.equal(resourceCount, 2)
  assert.equal(getAccessToken(), null)
  assert.equal(getRefreshToken(), null)
})

test('clears local tokens when refresh fails', async () => {
  clearAuthTokens()
  saveAuthTokens('old-access', 'refresh-1')

  installAdapter(refreshClient, () => ({
    status: 401,
    data: { code: 40104, msg: '刷新令牌无效或已过期', data: null },
  }))
  installAdapter(http, () => unauthorized())

  await assert.rejects(() => http.get('/sys-user/sys-users'))

  assert.equal(getAccessToken(), null)
  assert.equal(getRefreshToken(), null)
})

test('does not attempt refresh when no refresh token is stored', async () => {
  clearAuthTokens()
  localStorage.setItem(ACCESS_TOKEN_KEY, 'old-access')

  let refreshCount = 0
  installAdapter(refreshClient, () => {
    refreshCount += 1
    return ok({ accessToken: 'new-access' })
  })
  installAdapter(http, () => unauthorized())

  await assert.rejects(() => http.get('/sys-user/sys-users'))

  assert.equal(refreshCount, 0)
  assert.equal(getAccessToken(), null)
})
