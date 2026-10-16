import assert from 'node:assert/strict'
import test from 'node:test'
import { QueryClient, QueryObserver } from '@tanstack/react-query'
import { clearAuthenticatedQueryCache } from '../../src/services/authCache.ts'

test('late response from a cancelled account query cannot replace the next account cache', async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  let resolveOldRequest: ((value: string) => void) | undefined
  const oldRequest = queryClient.fetchQuery({
    queryKey: ['users'],
    queryFn: () => new Promise<string>((resolve) => { resolveOldRequest = resolve }),
  }).catch(() => undefined)
  await Promise.resolve()

  await clearAuthenticatedQueryCache(queryClient)
  queryClient.setQueryData(['users'], 'account-b')
  resolveOldRequest?.('account-a')
  await oldRequest

  assert.equal(queryClient.getQueryData(['users']), 'account-b')
  queryClient.clear()
})

test('rebuilding the protected account observer after cleanup validates the next account', async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  const queryKey = ['current-sys-user']
  const accountA = { id: 1, username: 'account-a' }
  const accountB = { id: 2, username: 'account-b' }
  queryClient.setQueryData(queryKey, accountA)
  let requests = 0
  const options = { queryKey, staleTime: Infinity, queryFn: async () => { requests += 1; return accountB } }
  const oldObserver = new QueryObserver(queryClient, options)
  oldObserver.subscribe(() => {})
  assert.equal(oldObserver.getCurrentResult().data, accountA)
  assert.equal(requests, 0)

  // The session gate unmounts the old protected tree before awaiting cache cleanup.
  oldObserver.destroy()
  await clearAuthenticatedQueryCache(queryClient)
  const nextObserver = new QueryObserver(queryClient, options)
  try {
    assert.equal(nextObserver.getCurrentResult().data, undefined)
    assert.equal(nextObserver.getCurrentResult().isPending, true)
    nextObserver.subscribe(() => {})
    await nextObserver.refetch()
    assert.equal(nextObserver.getCurrentResult().data, accountB)
    assert.equal(requests, 1)
  } finally {
    nextObserver.destroy()
    queryClient.clear()
  }
})
