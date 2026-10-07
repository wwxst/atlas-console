import assert from 'node:assert/strict'
import test from 'node:test'
import { AxiosError } from 'axios'
import { getApiErrorMessage, requireApiData, requireApiSuccess } from '../../src/services/api.ts'
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

test('does not leak an English backend message for a known status', () => {
  const error = axiosError(502)
  if (error.response) error.response.data.msg = 'Bad Gateway'
  assert.equal(getApiErrorMessage(error, 'fallback'), '服务暂时不可用，请稍后重试')
})

test('maps timeout and network failures to Chinese messages', () => {
  assert.equal(getApiErrorMessage(new AxiosError('timeout', 'ECONNABORTED'), 'fallback'), '请求超时，请稍后重试')
  assert.equal(getApiErrorMessage(new AxiosError('Network Error', 'ERR_NETWORK'), 'fallback'), '网络异常，请检查连接后重试')
})

test('maps a 429 response to a retry-later message', () => {
  assert.equal(getApiErrorMessage(axiosError(429), 'fallback'), '请求过于频繁，请稍后重试')
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

test('accepts a successful mutation with null data but still rejects missing query data', () => {
  assert.doesNotThrow(() => requireApiSuccess({ code: 200, msg: null, data: null }))
  assert.throws(() => requireApiData({ code: 200, msg: '配置响应缺少数据', data: null }), /配置响应缺少数据/)
})

test('publishes a rejected mutation instead of treating null data as success', () => {
  const messages: string[] = []
  const unsubscribe = subscribeRequestErrors((message) => messages.push(message))
  assert.throws(() => requireApiSuccess({ code: 40018, msg: '不能同时关闭所有认证渠道', data: null }), /不能同时关闭/)
  unsubscribe()
  assert.deepEqual(messages, ['不能同时关闭所有认证渠道'])
})
