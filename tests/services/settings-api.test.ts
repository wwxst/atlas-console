import assert from 'node:assert/strict'
import test from 'node:test'

Object.defineProperty(globalThis, 'localStorage', { value: { getItem: () => null }, configurable: true })
const { http } = await import('../../src/services/http.ts')
const { getAuthChannels, updateAuthChannel } = await import('../../src/features/settings/api.ts')

test('reads the server channel flags including retained code settings on a disabled channel', async () => {
  const flags = { phoneEnabled: false, phoneCodeEnabled: true, emailEnabled: true, emailCodeEnabled: false }
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'get')
    assert.equal(config.url, '/sys-user/auth-channels')
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: flags } }
  }
  assert.deepEqual(await getAuthChannels(), flags)
})

test('saves both required flags to the selected channel and accepts a void response', async () => {
  for (const channel of ['phone', 'email'] as const) {
    http.defaults.adapter = async (config) => {
      assert.equal(config.method, 'put')
      assert.equal(config.url, `/sys-user/auth-channels/${channel}`)
      assert.deepEqual(JSON.parse(config.data), { enabled: true, codeEnabled: false })
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: null } }
    }
    await updateAuthChannel(channel, { enabled: true, codeEnabled: false })
  }
})

test('rejects a failed channel update delivered as an HTTP 200 business response', async () => {
  http.defaults.adapter = async (config) => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: { code: 40018, msg: '至少保留一个认证渠道开启', data: null },
  })
  await assert.rejects(updateAuthChannel('phone', { enabled: false, codeEnabled: false }), /至少保留一个/)
})
