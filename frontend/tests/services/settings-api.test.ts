import assert from 'node:assert/strict'
import test from 'node:test'

Object.defineProperty(globalThis, 'localStorage', { value: { getItem: () => null }, configurable: true })
const { http } = await import('../../src/services/http.ts')
const api = await import('../../src/features/settings/api.ts')

test('reads login switches and selected SMS provider', async () => {
  const settings = { phoneLoginEnabled: true, emailLoginEnabled: false, smsProvider: 'tencent' }
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'get')
    assert.equal(config.url, '/sys-user/login-settings')
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: settings } }
  }
  assert.deepEqual(await api.getLoginSettings(), settings)
})

test('saves both login switches and SMS provider in one request', async () => {
  const settings = { phoneLoginEnabled: false, emailLoginEnabled: false, smsProvider: 'aliyun' }
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'put')
    assert.equal(config.url, '/sys-user/login-settings')
    assert.deepEqual(JSON.parse(config.data), settings)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: settings } }
  }
  assert.deepEqual(await api.updateLoginSettings(settings), settings)
})

test('reads both SMS provider configurations without returning credentials', async () => {
  const aliyun = { accessKeyId: 'example-id', signName: 'Atlas', templateCode: 'SMS_123', accessKeySecretConfigured: true, configured: true }
  const tencent = { secretId: 'example-id', sdkAppId: '1400000000', signName: 'Atlas', templateId: '123456', region: 'ap-guangzhou', secretKeyConfigured: true, configured: true }
  http.defaults.adapter = async (config) => {
    if (config.url.endsWith('/aliyun')) return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: aliyun } }
    if (config.url.endsWith('/tencent')) return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: tencent } }
    throw new Error('Unexpected URL')
  }
  assert.deepEqual(await api.getAliyunSmsSettings(), aliyun)
  assert.deepEqual(await api.getTencentSmsSettings(), tencent)
})

test('saves SMS provider configuration', async () => {
  const aliyunInput = { accessKeyId: 'example-id', accessKeySecret: 'example-value', signName: 'Atlas', templateCode: 'SMS_123' }
  const tencentInput = { secretId: 'example-id', secretKey: 'example-value', sdkAppId: '1400000000', signName: 'Atlas', templateId: '123456', region: 'ap-guangzhou' }
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'put')
    if (config.url.endsWith('/aliyun')) {
      assert.deepEqual(JSON.parse(config.data), aliyunInput)
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: { accessKeyId: 'example-id', signName: 'Atlas', templateCode: 'SMS_123', accessKeySecretConfigured: true, configured: true } } }
    }
    assert.equal(config.url.endsWith('/tencent'), true)
    assert.deepEqual(JSON.parse(config.data), tencentInput)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: { secretId: 'example-id', sdkAppId: '1400000000', signName: 'Atlas', templateId: '123456', region: 'ap-guangzhou', secretKeyConfigured: true, configured: true } } }
  }
  assert.equal((await api.updateAliyunSmsSettings(aliyunInput)).accessKeySecretConfigured, true)
  assert.equal((await api.updateTencentSmsSettings(tencentInput)).secretKeyConfigured, true)
})

test('saves SMTP settings and returns only a configured flag for the password', async () => {
  const input = { host: 'smtp.example.com', port: 465, username: 'sender@example.com', password: 'example-value', fromAddress: 'sender@example.com', fromName: 'Atlas', security: 'SSL' }
  const saved = { host: 'smtp.example.com', port: 465, username: 'sender@example.com', fromAddress: 'sender@example.com', fromName: 'Atlas', security: 'SSL', passwordConfigured: true, configured: true }
  http.defaults.adapter = async (config) => {
    assert.equal(config.url, '/sys-user/login-providers/email')
    if (config.method === 'put') assert.deepEqual(JSON.parse(config.data), input)
    else assert.equal(config.method, 'get')
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: saved } }
  }
  assert.deepEqual(await api.updateEmailLoginSettings(input), saved)
  assert.deepEqual(await api.getEmailLoginSettings(), saved)
})

test('rejects a failed login settings update delivered as an HTTP 200 business response', async () => {
  http.defaults.adapter = async (config) => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: { code: 40000, msg: '短信服务平台不正确', data: null },
  })
  await assert.rejects(api.updateLoginSettings({ phoneLoginEnabled: true, emailLoginEnabled: true, smsProvider: 'aliyun' }), /短信服务平台不正确/)
})
