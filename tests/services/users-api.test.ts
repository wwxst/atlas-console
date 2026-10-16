import assert from 'node:assert/strict'
import test from 'node:test'

Object.defineProperty(globalThis, 'localStorage', { value: { getItem: () => null }, configurable: true })
const { http } = await import('../../src/services/http.ts')
const { getUsers, updateUser, updateUserStatus } = await import('../../src/features/users/api.ts')
const { PartialSysUserUpdateError, updateSysUserWithAvatar } = await import('../../src/features/systemUsers/api.ts')

test('admin edits ordinary user contacts directly and receives the saved user', async () => {
  const input = { nickname: '用户昵称', phone: '13800138000', email: null }
  const saved = { id: '9', userNo: '583729', ...input, status: 1, createdAt: '2026-10-02T10:05:00', updatedAt: '2026-10-05T10:05:00' }
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'put')
    assert.equal(config.url, '/sys-user/users/9')
    assert.deepEqual(JSON.parse(config.data), input)
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: saved } }
  }
  assert.deepEqual(await updateUser('9', input), saved)
})

test('a contact conflict rejects the save instead of returning success', async () => {
  http.defaults.adapter = async (config) => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: { code: 40009, msg: '该联系方式已被绑定', data: null },
  })
  await assert.rejects(updateUser('9', { nickname: '用户昵称', phone: '13800138000', email: null }), /已被绑定/)
})

test('ordinary user status accepts successful void responses for disable and enable', async () => {
  for (const status of [0, 1] as const) {
    http.defaults.adapter = async (config) => {
      assert.equal(config.method, 'patch')
      assert.equal(config.url, '/sys-user/users/9/status')
      assert.deepEqual(JSON.parse(config.data), { status })
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: null } }
    }
    await updateUserStatus('9', status)
  }
})

test('a failed status change rejects an HTTP 200 business error', async () => {
  http.defaults.adapter = async (config) => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: { code: 40000, msg: '用户不存在', data: null },
  })
  await assert.rejects(updateUserStatus('9', 0), /用户不存在/)
})

test('preserves a Snowflake ID from list JSON through contact and status request URLs', async () => {
  const id = '9007199254740993'
  const input = { nickname: '用户昵称', phone: '13800138000', email: null }
  const saved = { id, userNo: '583729', ...input, status: 1, createdAt: '2026-10-02T10:05:00', updatedAt: '2026-10-05T10:05:00' }
  const calls: { method: string | undefined; url: string | undefined; body: unknown }[] = []
  http.defaults.adapter = async (config) => {
    calls.push({ method: config.method, url: config.url, body: config.data ? JSON.parse(config.data) : undefined })
    const data = config.method === 'get'
      ? { total: 1, page: 1, pageSize: 20, records: [saved] }
      : config.method === 'put' ? saved : null
    return { config, status: 200, statusText: 'OK', headers: {}, data: JSON.stringify({ code: 200, msg: null, data }) }
  }

  const users = await getUsers({ page: 1, pageSize: 20 })
  const user = users.records[0]
  assert.equal(user.id, id)
  const updated = await updateUser(user.id, input)
  assert.equal(updated.id, id)
  await updateUserStatus(updated.id, 0)
  await updateUserStatus(updated.id, 1)

  assert.deepEqual(calls, [
    { method: 'get', url: '/sys-user/users', body: undefined },
    { method: 'put', url: '/sys-user/users/9007199254740993', body: input },
    { method: 'patch', url: '/sys-user/users/9007199254740993/status', body: { status: 0 } },
    { method: 'patch', url: '/sys-user/users/9007199254740993/status', body: { status: 1 } },
  ])
})

test('reports saved profile fields when a later avatar upload fails', async () => {
  const saved = { id: 9, username: 'edited', nickname: '编辑后', avatar: '/files/1', status: 1, createdAt: '2026-10-02T10:05:00', updatedAt: '2026-10-08T10:05:00' }
  let request = 0
  http.defaults.adapter = async (config) => {
    request += 1
    if (request === 1) {
      assert.equal(config.method, 'put')
      assert.equal(config.url, '/sys-user/sys-users/9')
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: saved } }
    }
    assert.equal(config.method, 'post')
    assert.equal(config.url, '/sys-user/sys-users/9/avatar')
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 40000, msg: '头像上传失败', data: null } }
  }

  const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
  await assert.rejects(
    updateSysUserWithAvatar(9, { username: 'edited', nickname: '编辑后' }, file),
    (error: unknown) => error instanceof PartialSysUserUpdateError
      && error.savedUser === saved
      && error.message.includes('资料已保存'),
  )
})
