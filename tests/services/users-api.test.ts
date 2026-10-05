import assert from 'node:assert/strict'
import test from 'node:test'

Object.defineProperty(globalThis, 'localStorage', { value: { getItem: () => null }, configurable: true })
const { http } = await import('../../src/services/http.ts')
const { updateUser } = await import('../../src/features/users/api.ts')

test('admin edits ordinary user contacts directly and receives the saved user', async () => {
  const input = { nickname: '用户昵称', phone: '13800138000', email: null }
  const saved = { id: 9, ...input, status: 1, createdAt: '2026-10-02T10:05:00', updatedAt: '2026-10-05T10:05:00' }
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'put')
    assert.equal(config.url, '/sys-user/users/9')
    assert.deepEqual(JSON.parse(config.data), input)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: saved } }
  }
  assert.deepEqual(await updateUser(9, input), saved)
})

test('a contact conflict rejects the save instead of returning success', async () => {
  http.defaults.adapter = async (config) => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: { code: 40009, msg: '该联系方式已被绑定', data: null },
  })
  await assert.rejects(updateUser(9, { nickname: '用户昵称', phone: '13800138000', email: null }), /已被绑定/)
})
