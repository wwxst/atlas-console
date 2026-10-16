import assert from 'node:assert/strict'
import test from 'node:test'
import { createElement } from 'react'
import { renderToString } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { createServer } from 'vite'

test('protected routes wait for session cleanup even when a previous account is cached', async () => {
  const tokens = new Map([
    ['atlas-access-token', 'account-b-access'],
    ['atlas-refresh-token', 'account-b-refresh'],
  ])
  Object.defineProperty(globalThis, 'localStorage', { value: { getItem: (key: string) => tokens.get(key) ?? null }, configurable: true })
  const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  try {
    const { default: RequireAdmin } = await server.ssrLoadModule('/src/router/RequireAdmin.tsx')
    queryClient.setQueryData(['current-sys-user'], { id: 1, username: 'account-a', nickname: '账号A' })
    const render = () => renderToString(createElement(QueryClientProvider, { client: queryClient },
      createElement(MemoryRouter, { initialEntries: ['/users'] },
        createElement(Routes, null,
          createElement(Route, { element: createElement(RequireAdmin) },
            createElement(Route, { path: '/users', element: createElement('div', null, 'previous-account-details') })),
          createElement(Route, { path: '/login', element: createElement('div', null, 'login') })),
      )))

    assert.match(render(), /正在验证登录状态/)
    assert.doesNotMatch(render(), /previous-account-details/)
    tokens.clear()
    assert.equal(render(), '')
  } finally {
    queryClient.clear()
    await server.close()
  }
})
