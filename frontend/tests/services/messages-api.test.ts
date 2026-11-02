import assert from 'node:assert/strict'
import test from 'node:test'

Object.defineProperty(globalThis, 'localStorage', { value: { getItem: () => null }, configurable: true })
const { http } = await import('../../src/services/http.ts')
const api = await import('../../src/features/messages/api.ts')

const template = {
  id: 7,
  provider: 'aliyun',
  name: '用户下单提醒',
  templateCode: 'SMS_123456',
  kind: 'notification',
  content: '订单号：${order_id}',
  variables: ['order_id'],
  reviewStatus: 'approved',
  enabled: true,
  updatedAt: '2026-11-02T10:00:00',
}

const notification = {
  id: 24,
  category: 'member',
  code: 'order_created',
  name: '用户下单客服提醒',
  description: '用户下单后通知客服',
  channels: { siteMessage: true, officialAccount: true, sms: false, enterpriseWechat: true },
  siteMessageTitle: '新订单提醒',
  siteMessageContent: '订单号：${order_id}',
  smsTemplateId: 7,
  smsVariableMappings: { order_id: 'orderNo' },
  updatedAt: '2026-11-02T10:00:00',
}

test('lists SMS templates with pagination and filters', async () => {
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'get')
    assert.equal(config.url, '/sys-user/sms-templates')
    assert.deepEqual(config.params, { page: 1, pageSize: 20, provider: 'aliyun', reviewStatus: 'approved' })
    return {
      config, status: 200, statusText: 'OK', headers: {},
      data: { code: 200, msg: null, data: { total: 1, page: 1, pageSize: 20, records: [template] } },
    }
  }
  assert.deepEqual(await api.getSmsTemplates({ page: 1, pageSize: 20, provider: 'aliyun', reviewStatus: 'approved' }), {
    total: 1, page: 1, pageSize: 20, records: [template],
  })
})

test('creates, updates and deletes an SMS template', async () => {
  const input = {
    provider: 'aliyun',
    name: '用户下单提醒',
    templateCode: 'SMS_123456',
    kind: 'notification',
    content: '订单号：${order_id}',
    variables: ['order_id'],
    reviewStatus: 'approved',
    enabled: true,
  }
  let call = 0
  http.defaults.adapter = async (config) => {
    call += 1
    if (call === 1) {
      assert.equal(config.method, 'post')
      assert.equal(config.url, '/sys-user/sms-templates')
      assert.deepEqual(JSON.parse(config.data), input)
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: template } }
    }
    if (call === 2) {
      assert.equal(config.method, 'put')
      assert.equal(config.url, '/sys-user/sms-templates/7')
      assert.deepEqual(JSON.parse(config.data), input)
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: template } }
    }
    assert.equal(config.method, 'delete')
    assert.equal(config.url, '/sys-user/sms-templates/7')
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: null } }
  }
  assert.deepEqual(await api.createSmsTemplate(input), template)
  assert.deepEqual(await api.updateSmsTemplate(7, input), template)
  await api.deleteSmsTemplate(7)
})

test('lists message notification scenarios by category', async () => {
  http.defaults.adapter = async (config) => {
    assert.equal(config.method, 'get')
    assert.equal(config.url, '/sys-user/message-notifications')
    assert.deepEqual(config.params, { category: 'member' })
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: [notification] } }
  }
  assert.deepEqual(await api.getMessageNotifications('member'), [notification])
})

test('creates, updates and deletes message notification scenarios', async () => {
  const input = {
    category: 'member',
    code: 'order_created',
    name: '用户下单客服提醒',
    description: '用户下单后通知客服',
    channels: { siteMessage: true, officialAccount: true, sms: false, enterpriseWechat: true },
    siteMessageTitle: '新订单提醒',
    siteMessageContent: '订单号：${order_id}',
    smsTemplateId: 7,
    smsVariableMappings: { order_id: 'orderNo' },
  }
  let call = 0
  http.defaults.adapter = async (config) => {
    call += 1
    if (call === 1) {
      assert.equal(config.method, 'post')
      assert.equal(config.url, '/sys-user/message-notifications')
      assert.deepEqual(JSON.parse(config.data), input)
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: notification } }
    }
    if (call === 2) {
      assert.equal(config.method, 'put')
      assert.equal(config.url, '/sys-user/message-notifications/24')
      assert.deepEqual(JSON.parse(config.data), input)
      return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: notification } }
    }
    assert.equal(config.method, 'delete')
    assert.equal(config.url, '/sys-user/message-notifications/24')
    return { config, status: 200, statusText: 'OK', headers: {}, data: { code: 200, msg: null, data: null } }
  }
  assert.deepEqual(await api.createMessageNotification(input), notification)
  assert.deepEqual(await api.updateMessageNotification(24, input), notification)
  await api.deleteMessageNotification(24)
})
