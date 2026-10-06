import assert from 'node:assert/strict'
import test from 'node:test'
import * as ossRegions from '../../src/pages/aliyunOssRegions.ts'

Object.defineProperty(globalThis, 'localStorage', { value: { getItem: () => null }, configurable: true })
const { http } = await import('../../src/services/http.ts')
const storage = await import('../../src/services/storage.ts')
const { subscribeRequestErrors } = await import('../../src/services/requestErrorBus.ts')

test('storage operations send selected Bucket permission without repeating credentials', async () => {
  const space = { id: 17, bucketName: 'atlas-images', regionId: 'oss-cn-hangzhou', accessDomain: null, isDefault: false, createdAt: '2026-10-06T10:00:00', updatedAt: '2026-10-06T10:00:00' }
  const calls: { method: string | undefined; url: string | undefined; body: unknown; params: unknown }[] = []
  const responses = [
    { storageType: 'local' }, { storageType: 'aliyun' }, { configured: false }, { configured: true },
    { page: 2, pageSize: 15, total: 20, records: [space] }, space, { syncedCount: 3 },
    { ...space, isDefault: true }, { ...space, accessDomain: 'https://files.example.com' }, null,
  ]
  http.defaults.adapter = async (config) => {
    calls.push({ method: config.method, url: config.url, body: config.data ? JSON.parse(config.data) : undefined, params: config.params })
    const message = config.method === 'put' && config.url === '/sys-user/storage/settings' ? '存储配置已保存' : null
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: message, data: responses.shift() } }
  }
  assert.deepEqual(await storage.getStorageSettings(), { storageType: 'local' })
  assert.deepEqual(await storage.updateStorageSettings({ storageType: 'aliyun' }), { data: { storageType: 'aliyun' }, message: '存储配置已保存' })
  assert.deepEqual(await storage.getOssCredentials(), { configured: false })
  assert.deepEqual(await storage.updateOssCredentials({ accessKeyId: 'test-id', accessKeySecret: 'test-secret' }), { configured: true })
  assert.equal((await storage.getStorageSpaces({ page: 2, pageSize: 15 })).total, 20)
  assert.equal((await storage.bindStorageSpace({ bucketName: space.bucketName, regionId: space.regionId, accessPermission: 'public-read-write' })).id, 17)
  assert.deepEqual(await storage.syncStorageSpaces(), { syncedCount: 3 })
  assert.equal((await storage.setDefaultStorageSpace(17)).isDefault, true)
  assert.equal((await storage.updateStorageSpaceDomain(17, { accessDomain: 'https://files.example.com' })).accessDomain, 'https://files.example.com')
  await storage.deleteStorageSpace(17)
  assert.deepEqual(calls.map(({ method, url }) => [method, url]), [
    ['get', '/sys-user/storage/settings'], ['put', '/sys-user/storage/settings'],
    ['get', '/sys-user/storage/oss/credentials'], ['put', '/sys-user/storage/oss/credentials'],
    ['get', '/sys-user/storage/spaces'], ['post', '/sys-user/storage/spaces'],
    ['post', '/sys-user/storage/spaces/sync'], ['put', '/sys-user/storage/spaces/17/default'],
    ['patch', '/sys-user/storage/spaces/17/domain'], ['delete', '/sys-user/storage/spaces/17'],
  ])
  assert.deepEqual(calls[1].body, { storageType: 'aliyun' })
  assert.deepEqual(calls[3].body, { accessKeyId: 'test-id', accessKeySecret: 'test-secret' })
  assert.deepEqual(calls[4].params, { page: 2, pageSize: 15 })
  assert.deepEqual(calls[5].body, { bucketName: space.bucketName, regionId: space.regionId, accessPermission: 'public-read-write' })
  assert.deepEqual(calls[8].body, { accessDomain: 'https://files.example.com' })
})

test('server OSS region IDs keep Chinese labels and select the corresponding grouped option', () => {
  assert.equal(ossRegions.getAliyunOssRegionLabel('oss-cn-hangzhou'), '华东1（杭州）')
  assert.equal(ossRegions.getAliyunOssRegionLabel('cn-hangzhou'), '华东1（杭州）')
  assert.equal(ossRegions.getAliyunOssRegionLabel('oss-ap-southeast-1'), '新加坡')
  assert.equal(ossRegions.getAliyunOssRegionLabel('oss-future-region'), 'oss-future-region')
  const options = ossRegions.aliyunOssRegionGroups.flatMap(({ regions }) => regions)
  const selected = options.find(({ id }) => id === ossRegions.getAliyunOssRegionId('oss-cn-hangzhou'))
  assert.equal(selected?.label, '华东1（杭州）')
  assert.equal(ossRegions.getAliyunOssDefaultDomain('atlas-console-oss-2026', 'oss-cn-shanghai'), 'https://atlas-console-oss-2026.oss-cn-shanghai.aliyuncs.com')
})

test('storage business errors reject and reach the shared request error bus once', async () => {
  const messages: string[] = []
  const unsubscribe = subscribeRequestErrors((message) => messages.push(message))
  http.defaults.adapter = async (config) => ({ config, status: 200, statusText: 'OK', headers: {}, data: { code: 400, msg: '默认空间不能删除', data: null } })
  try {
    await assert.rejects(storage.deleteStorageSpace(17), /默认空间不能删除/)
    assert.deepEqual(messages, ['默认空间不能删除'])
  } finally {
    unsubscribe()
  }
})
