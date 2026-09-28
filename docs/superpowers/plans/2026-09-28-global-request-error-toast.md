# Global Request Error Toast Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将所有请求层和后端业务失败统一转换为中文全局 Toast，并避免页面重复展示和查询重试导致的重复弹窗。

**Architecture:** 在 `src/services/requestErrorBus.ts` 中提供与 React 解耦的错误通知通道和短时间去重；`src/services/api.ts` 负责状态码/网络错误中文化并发布业务码失败；`src/services/http.ts` 负责发布 Axios 响应错误。`src/ui/RequestErrorToast.tsx` 在应用根部订阅通道并使用现有 `Toast` 组件自动显示和关闭，页面仅保留失败状态及操作入口。

**Tech Stack:** React 19, TypeScript, Axios, TanStack Query, Node 24 built-in test runner, existing Less/CSS Modules.

---

### Task 1: Add failing request error normalization tests

**Files:**
- Create: `tests/services/api-errors.test.ts`
- Modify: `package.json:5-12`

- [ ] **Step 1: Write the failing tests**

Create tests that assert status-code mapping, timeout/network fallback, business-error publication, and duplicate suppression:

```ts
import assert from 'node:assert/strict'
import test from 'node:test'
import axios, { AxiosError } from 'axios'
import { getApiErrorMessage, requireApiData } from '../../src/services/api.ts'
import { emitRequestError, subscribeRequestErrors } from '../../src/services/requestErrorBus.ts'

function axiosError(status: number, code = 'ERR_BAD_RESPONSE') {
  return new AxiosError(`Request failed with status code ${status}`, code, undefined, undefined, {
    status,
    statusText: String(status),
    headers: {},
    config: {} as never,
    data: { code: status, msg: null, data: null },
  })
}

test('maps a 502 response to a Chinese service-unavailable message', () => {
  assert.equal(getApiErrorMessage(axiosError(502), 'fallback'), '服务暂时不可用，请稍后重试')
})

test('maps timeout and network failures to Chinese messages', () => {
  assert.equal(getApiErrorMessage(new AxiosError('timeout', 'ECONNABORTED'), 'fallback'), '请求超时，请稍后重试')
  assert.equal(getApiErrorMessage(new AxiosError('Network Error', 'ERR_NETWORK'), 'fallback'), '网络异常，请检查连接后重试')
})

test('publishes a business-code failure and preserves its Chinese message', () => {
  const messages: string[] = []
  const unsubscribe = subscribeRequestErrors((message) => messages.push(message))
  assert.throws(() => requireApiData({ code: 500, msg: '系统暂时不可用', data: null }), /系统暂时不可用/)
  unsubscribe()
  assert.deepEqual(messages, ['系统暂时不可用'])
})

test('suppresses duplicate messages during the dedupe window', () => {
  const messages: string[] = []
  const unsubscribe = subscribeRequestErrors((message) => messages.push(message))
  const message = `重复测试-${Date.now()}`
  emitRequestError(message)
  emitRequestError(message)
  unsubscribe()
  assert.deepEqual(messages, [message])
})

assert.equal(axios.isAxiosError(axiosError(500)), true)
```

- [ ] **Step 2: Add a direct test command**

Add this script to `package.json` without changing dependencies:

```json
"test": "node --experimental-strip-types --test tests/services/api-errors.test.ts"
```

- [ ] **Step 3: Run the tests and verify they fail for the missing behavior**

Run: `npm test`

Expected: FAIL because `src/services/requestErrorBus.ts` and the new notification behavior do not exist yet, not because of a test syntax error.

### Task 2: Implement the request error channel and Chinese normalization

**Files:**
- Create: `src/services/requestErrorBus.ts`
- Modify: `src/services/api.ts:1-32`
- Modify: `src/services/http.ts:1-20`

- [ ] **Step 1: Implement the minimal deduplicating event bus**

Create `requestErrorBus.ts` with a `Set` of listeners, a `Map` keyed by message, a 1.5-second dedupe window, and an unsubscribe function. `emitRequestError` must notify each listener only when the same message has not been emitted in the window.

- [ ] **Step 2: Implement Chinese error mapping**

Update `getApiErrorMessage` so Axios errors use backend `msg` first, then map `401`, `403`, `404`, `408`, `429`, all `5xx`, timeout codes, network failures, and unknown Axios failures to Chinese messages. Non-Axios business `Error` instances retain their message; the supplied fallback remains the final result.

- [ ] **Step 3: Publish business-code failures**

In `requireApiData`, compute the error text, call `emitRequestError`, then throw the same `Error` so existing Query/mutation state remains intact.

- [ ] **Step 4: Publish transport failures**

In the Axios response rejection handler, keep the existing 401 token cleanup, call `emitRequestError(getApiErrorMessage(error, '请求失败，请稍后重试'))`, and rethrow the original error.

- [ ] **Step 5: Run the focused tests**

Run: `npm test`

Expected: PASS with all four tests green.

### Task 3: Mount the global Toast and remove duplicate page messages

**Files:**
- Create: `src/ui/RequestErrorToast.tsx`
- Modify: `src/ui/index.tsx:1-9`
- Modify: `src/App.tsx:1-18`
- Modify: `src/pages/LoginPage.tsx:1-105`
- Modify: `src/pages/SystemUsersPage.tsx:1-117`
- Modify: `src/router/RequireAdmin.tsx:1-48`

- [ ] **Step 1: Implement the Toast host**

Subscribe to `requestErrorBus` in an effect, store the latest `{ id, message }`, render `<Toast open tone="danger">message</Toast>`, and clear it after 4 seconds. Clear the timer on replacement and unmount.

- [ ] **Step 2: Export and mount it once**

Export `RequestErrorToast` from `src/ui/index.tsx` and render it beside `AppRouter` in `App.tsx`, so it remains available on both `/login` and protected routes.

- [ ] **Step 3: Remove duplicate concrete error text**

Remove page imports and `getApiErrorMessage` text rendering from LoginPage, SystemUsersPage, and RequireAdmin. Keep LoginPage field validation and success Toast; keep SystemUsersPage's table failure row and retry button with a static Chinese state label; keep RequireAdmin's heading, retry, and logout actions without rendering the transport error string.

- [ ] **Step 4: Run focused tests and lint**

Run: `npm test` then `npm run lint`

Expected: all tests pass and oxlint reports no errors.

### Task 4: Record the canonical behavior and verify the application

**Files:**
- Modify: `docs/architecture.md` under Data Flow
- Modify: `docs/ui-system.md` under Toast/component contract

- [ ] **Step 1: Document the request error flow**

Record that Axios transport failures and `requireApiData` business failures publish to the shared request-error channel, while pages retain only retry/loading state.

- [ ] **Step 2: Document Toast behavior**

Record the global placement, danger semantics, 4-second dismissal, short-window duplicate suppression, and Chinese status-code fallback policy.

- [ ] **Step 3: Run the required verification**

Run: `npm test`, `npm run lint`, `npm run build`, and `git diff --check`.

Expected: all commands exit 0; no `Request failed with status code` string remains in `src` error display paths.

- [ ] **Step 4: Review the final diff**

Run: `git status --short --branch` and `git diff --stat`; confirm only the request-error implementation, affected pages, canonical docs, test script/test, and the already-existing untracked `pnpm-lock.yaml` are present.
