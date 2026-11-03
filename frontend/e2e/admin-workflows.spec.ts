import { expect, test, type Page } from '@playwright/test'

const userId = '9007199254740993'
const now = '2026-10-01T12:00:00'

async function installMockApi(page: Page, deploymentReady = false, previousRelease = false,
  releaseHistory = false, scenario: 'normal' | 'no-release' | 'up-to-date' | 'baota' = 'normal') {
  let deploymentRequested = false
  let nickname = '测试用户'
  let status = 1
  let systemUserStatus = 1
  let loginSettings = { phoneLoginEnabled: true, emailLoginEnabled: true, smsProvider: 'aliyun' as 'aliyun' | 'tencent' }
  let aliyunConfig = { accessKeyId: '', signName: '', templateCode: '', accessKeySecretConfigured: false, configured: false }
  let tencentConfig = { secretId: '', sdkAppId: '', signName: '', templateId: '', region: 'ap-guangzhou', secretKeyConfigured: false, configured: false }
  let emailConfig = { host: '', port: 465, username: '', fromAddress: '', fromName: 'Atlas', security: 'SSL', passwordConfigured: false, configured: false }
  const requests: string[] = []

  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    const method = route.request().method()
    const reply = (data: unknown) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, msg: null, data }),
    })

    if (path === '/api/sys-user/auth/login' && method === 'POST') {
      return reply({ accessToken: 'test-access-token', refreshToken: 'test-refresh-token' })
    }
    if (path === '/api/sys-user/auth/refresh') {
      return reply({ accessToken: 'test-access-token-renewed' })
    }
    if (path === '/api/sys-user/auth/me') {
      return reply({ id: 1, username: 'admin', nickname: '测试管理员' })
    }
    if (path === '/api/sys-user/deployment' && method === 'GET') {
      const noRelease = scenario === 'no-release'
      const upToDate = scenario === 'up-to-date'
      const releaseUrl = 'https://github.com/wwxst/atlas-console/releases/tag/v0.1.1'
      return reply({
        configured: deploymentReady,
        state: !deploymentReady ? 'not_configured'
          : deploymentRequested ? 'in_progress' : noRelease ? 'no_release' : 'idle',
        conclusion: null,
        runUrl: noRelease ? null : releaseUrl,
        title: noRelease ? null : 'Atlas Console v0.1.1',
        requestId: deploymentRequested ? 'mock-request' : null,
        runId: null,
        recoveryAvailable: false,
        rollbackAvailable: previousRelease,
        deploymentMode: scenario === 'baota' ? 'baota' : 'docker',
        currentVersion: upToDate ? '0.1.1' : '0.1.0',
        latestVersion: noRelease ? null : '0.1.1',
        latestReleaseUrl: noRelease ? null : releaseUrl,
        releaseNotes: noRelease ? null : '修复登录问题并优化系统更新',
        publishedAt: '2026-11-02T12:00:00Z',
        updateAvailable: deploymentReady && !noRelease && !upToDate,
        releaseCheckError: noRelease ? 'GitHub 尚未发布正式版本' : null,
        message: deploymentRequested ? '更新任务已启动' : null,
      })
    }
    if (path === '/api/sys-user/deployment/history' && method === 'GET') {
      const a = 'a'.repeat(40)
      const b = 'b'.repeat(40)
      const c = 'c'.repeat(40)
      const d = 'd'.repeat(40)
      const e = 'e'.repeat(40)
      const f = 'f'.repeat(40)
      return reply(releaseHistory ? [
        { releaseId: c + '_' + d, version: 'v0.1.0', frontendSha: c, backendSha: d, deployedAt: '2026-10-28T10:00:00Z', current: true },
        { releaseId: a + '_' + b, version: 'v0.0.9', frontendSha: a, backendSha: b, deployedAt: '2026-10-27T10:00:00Z', current: false },
        { releaseId: e + '_' + f, version: 'v0.0.8', frontendSha: e, backendSha: f, deployedAt: '2026-10-26T10:00:00Z', current: false },
      ] : [])
    }
    if (path === '/api/sys-user/deployment/rollback/history' && method === 'POST') {
      const data = route.request().postDataJSON() as { releaseId: string }
      deploymentRequested = true
      requests.push('historical-rollback:' + data.releaseId)
      return reply({ configured: true, state: 'in_progress', conclusion: null,
        title: '回滚历史版本', requestId: 'mock-request', rollbackAvailable: true })
    }
    if (path === '/api/sys-user/deployment/rollback' && method === 'POST') {
      deploymentRequested = true
      requests.push('rollback-requested')
      return reply({ configured: true, state: 'in_progress', conclusion: null,
        title: '回滚上一版本', requestId: 'mock-request', rollbackAvailable: true })
    }
    if (path === '/api/sys-user/deployment' && method === 'POST') {
      deploymentRequested = true
      requests.push('deployment-requested')
      return reply({ configured: true, state: 'in_progress', conclusion: null,
        title: '正在安装 v0.1.1', requestId: 'mock-request', updateAvailable: true,
        currentVersion: '0.1.0', latestVersion: '0.1.1' })
    }
    if (path === '/api/sys-user/sys-users' && method === 'GET') {
      requests.push('system-users-order:' + (url.searchParams.get('createdAtOrder') ?? 'id-asc'))
      return reply({ page: 1, pageSize: 20, total: 2, records: [
        { id: 1, username: 'admin', nickname: '测试管理员', avatar: null,
          online: true, status: 1, createdAt: now, updatedAt: now },
        { id: 7, username: 'operator', nickname: '测试操作员', avatar: null,
          online: false, status: systemUserStatus, createdAt: now, updatedAt: now },
      ] })
    }
    if (path.startsWith('/api/sys-user/sys-users/') && path.endsWith('/status') && method === 'PATCH') {
      const id = Number(path.split('/')[4])
      const nextStatus = (route.request().postDataJSON() as { status: number }).status
      requests.push(`system-status:${id}:${nextStatus}`)
      if (id === 7) systemUserStatus = nextStatus
      return reply(null)
    }
    if (path === '/api/sys-user/users' && method === 'GET') {
      return reply({ page: 1, pageSize: 20, total: 1, records: [{
        id: userId, userNo: '123456', nickname, phone: '13800138000',
        email: 'user@example.com', avatar: null, online: true, status, createdAt: now, updatedAt: now,
      }] })
    }
    if (path === `/api/sys-user/users/${userId}` && method === 'PUT') {
      const data = route.request().postDataJSON() as { nickname: string; phone: string | null; email: string | null }
      nickname = data.nickname
      requests.push('edit:' + userId)
      return reply({ id: userId, userNo: '123456', nickname, phone: data.phone,
        email: data.email, avatar: null, online: true, status, createdAt: now, updatedAt: now })
    }
    if (path === `/api/sys-user/users/${userId}/status` && method === 'PATCH') {
      status = (route.request().postDataJSON() as { status: number }).status
      requests.push('status:' + userId)
      return reply(null)
    }
    if (path === '/api/sys-user/login-settings' && method === 'GET') return reply(loginSettings)
    if (path === '/api/sys-user/login-settings' && method === 'PUT') {
      loginSettings = route.request().postDataJSON() as typeof loginSettings
      requests.push(`login-settings:${loginSettings.phoneLoginEnabled}:${loginSettings.emailLoginEnabled}:${loginSettings.smsProvider}`)
      return reply(loginSettings)
    }
    if (path === '/api/sys-user/login-providers/sms/aliyun' && method === 'GET') return reply(aliyunConfig)
    if (path === '/api/sys-user/login-providers/sms/aliyun' && method === 'PUT') {
      const data = route.request().postDataJSON() as { accessKeyId: string; accessKeySecret: string; signName: string; templateCode: string }
      aliyunConfig = { accessKeyId: data.accessKeyId, signName: data.signName, templateCode: data.templateCode, accessKeySecretConfigured: Boolean(data.accessKeySecret) || aliyunConfig.accessKeySecretConfigured, configured: true }
      requests.push('aliyun-sms-saved')
      return reply(aliyunConfig)
    }
    if (path === '/api/sys-user/login-providers/sms/tencent' && method === 'GET') return reply(tencentConfig)
    if (path === '/api/sys-user/login-providers/sms/tencent' && method === 'PUT') {
      const data = route.request().postDataJSON() as { secretId: string; secretKey: string; sdkAppId: string; signName: string; templateId: string; region: string }
      tencentConfig = { secretId: data.secretId, sdkAppId: data.sdkAppId, signName: data.signName, templateId: data.templateId, region: data.region, secretKeyConfigured: Boolean(data.secretKey) || tencentConfig.secretKeyConfigured, configured: true }
      requests.push('tencent-sms-saved')
      return reply(tencentConfig)
    }
    if (path === '/api/sys-user/login-providers/email' && method === 'GET') return reply(emailConfig)
    if (path === '/api/sys-user/login-providers/email' && method === 'PUT') {
      const data = route.request().postDataJSON() as { host: string; port: number; username: string; password: string; fromAddress: string; fromName: string; security: string }
      emailConfig = { host: data.host, port: data.port, username: data.username, fromAddress: data.fromAddress, fromName: data.fromName, security: data.security, passwordConfigured: Boolean(data.password) || emailConfig.passwordConfigured, configured: true }
      requests.push('email-smtp-saved')
      return reply(emailConfig)
    }
    if (path === '/api/sys-user/sms-templates' && method === 'GET') {
      return reply({ page: Number(url.searchParams.get('page') ?? 1), pageSize: Number(url.searchParams.get('pageSize') ?? 20), total: 0, records: [] })
    }
    if (path === '/api/sys-user/message-notifications' && method === 'GET') return reply([])
    if (path === '/api/sys-user/storage/settings') {
      return reply({ storageType: 'local' })
    }
    if (path === '/api/sys-user/storage/oss/credentials') return reply({ configured: false })
    if (path === '/api/sys-user/storage/spaces') {
      return reply({ page: 1, pageSize: 15, total: 0, records: [] })
    }
    if (path === '/api/sys-user/auth/logout') return reply(null)
    throw new Error(`Unexpected API request: ${method} ${path}`)
  })
  return requests
}

async function login(page: Page) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入登录账号').fill('admin')
  await page.getByPlaceholder('请输入登录密码').fill('test-password')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/$/)
}

test('admin logs in, edits a user, and changes account status with an exact string ID', async ({ page }) => {
  const requests = await installMockApi(page)
  await login(page)
  await page.goto('/users')
  await expect(page.locator('thead th').first()).toHaveText('编号')
  await expect(page.locator('thead th').nth(1)).toHaveText('头像')
  await expect(page.locator('thead th').nth(2)).toHaveText('昵称')
  await expect(page.locator('thead th').nth(6)).toHaveText('在线状态')
  await expect(page.locator('thead th').nth(7)).toHaveText('账号状态')
  await expect(page.locator('thead th').first()).toHaveCSS('height', '40px')
  await expect(page.locator('thead th').last()).toHaveCSS('text-align', 'center')
  const userRow = page.getByRole('row', { name: /测试用户/ })
  await expect(userRow.locator('td').first()).toHaveCSS('height', '46px')
  await expect(userRow.locator('td').first()).toHaveCSS('padding-left', '12px')
  await expect(userRow.locator('td').nth(1).locator('span').first()).toBeVisible()
  await expect(userRow.locator('td').nth(1).locator('span').first()).toHaveCSS('width', '30px')
  await expect(userRow.locator('td').nth(2)).toHaveText('测试用户')
  await expect(userRow.locator('td').nth(6)).toHaveText('在线')
  await expect(userRow.locator('td').nth(7)).toHaveText('正常')
  await expect(userRow.locator('td').last()).toHaveCSS('text-align', 'center')
  await expect(userRow.locator('td').last().locator('div')).toHaveCSS('gap', '8px')
  await expect(userRow.locator('td').last().locator('button').first()).toHaveCSS('height', '28px')
  await expect(page.getByRole('cell', { name: '123456' })).toBeVisible()
  await page.getByRole('button', { name: '查看', exact: true }).click()
  await expect(page.getByText('编号：123456')).toBeVisible()
  await page.getByRole('button', { name: '编辑', exact: true }).click()
  await page.locator('input[name="nickname"]').fill('修改后的昵称')
  await page.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('修改后的昵称').first()).toBeVisible()
  expect(requests).toContain('edit:' + userId)
  await page.getByRole('button', { name: '关闭', exact: true }).click()

  await page.getByRole('button', { name: '停用', exact: true }).click()
  await expect(page.getByRole('button', { name: '启用', exact: true })).toBeVisible()
  expect(requests).toContain('status:' + userId)
})

test('system-user actions are centered and the root admin cannot be disabled', async ({ page }) => {
  const requests = await installMockApi(page)
  await login(page)
  await page.goto('/system-users')
  await expect(page.locator('thead th').first()).toHaveText('编号')
  await expect(page.getByRole('button', { name: '创建时间，按创建时间升序' })).toBeVisible()
  await expect(page.locator('thead th').nth(1)).toHaveText('头像')
  await expect(page.locator('thead th').nth(2)).toHaveText('昵称')
  await expect(page.locator('thead th').nth(5)).toHaveText('在线状态')
  await expect(page.locator('thead th').nth(6)).toHaveText('账号状态')
  await expect(page.locator('thead th').first()).toHaveCSS('height', '40px')
  await expect(page.locator('thead th').last()).toHaveCSS('text-align', 'center')
  const adminRow = page.getByRole('row', { name: /测试管理员/ })
  await expect(adminRow.locator('td').first()).toHaveText('1')
  await expect(adminRow.locator('td').first()).toHaveCSS('height', '46px')
  await expect(adminRow.locator('td').first()).toHaveCSS('padding-left', '12px')
  expect(requests).toContain('system-users-order:id-asc')
  await page.getByRole('button', { name: '创建时间，按创建时间升序' }).click()
  await expect.poll(() => requests.at(-1)).toBe('system-users-order:asc')
  await page.getByRole('button', { name: '创建时间，按创建时间降序' }).click()
  await expect.poll(() => requests.at(-1)).toBe('system-users-order:desc')
  await page.getByRole('button', { name: '创建时间，取消创建时间排序' }).click()
  await expect(page.getByRole('button', { name: '创建时间，按创建时间升序' })).toBeVisible()
  await expect(adminRow.locator('td').nth(1).locator('span').first()).toBeVisible()
  await expect(adminRow.locator('td').nth(1).locator('span').first()).toHaveCSS('width', '30px')
  await expect(adminRow.locator('td').nth(2)).toHaveText('测试管理员')
  await expect(adminRow.locator('td').nth(5)).toHaveText('在线')
  await expect(adminRow.locator('td').nth(6)).toHaveText('正常')
  await expect(adminRow.locator('td').last()).toHaveCSS('text-align', 'center')
  await expect(adminRow.locator('td').last().locator('div')).toHaveCSS('gap', '8px')
  await expect(adminRow.locator('td').last().locator('button').first()).toHaveCSS('height', '28px')
  await expect(adminRow).not.toContainText('超级管理员')
  await expect(adminRow.getByRole('button', { name: '停用', exact: true })).toBeDisabled()

  const operatorRow = page.getByRole('row', { name: /测试操作员/ })
  await expect(operatorRow.locator('td').nth(5)).toHaveText('离线')
  await expect(operatorRow.locator('td').first()).toHaveText('7')
  await expect(operatorRow.locator('td').last()).toHaveCSS('text-align', 'center')
  await operatorRow.getByRole('button', { name: '停用', exact: true }).click()
  await expect(operatorRow.getByRole('button', { name: '启用', exact: true })).toBeVisible()
  await operatorRow.getByRole('button', { name: '启用', exact: true }).click()
  await expect(operatorRow.getByRole('button', { name: '停用', exact: true })).toBeVisible()
  expect(requests).toContain('system-status:7:0')
  expect(requests).toContain('system-status:7:1')
  expect(requests).not.toContain('system-status:1:0')

  await adminRow.getByRole('button', { name: '查看' }).click()
  await expect(page.getByText('编号：1')).toBeVisible()
})

test('login settings contain SMS configuration tabs while message management stays separate', async ({ page }) => {
  const requests = await installMockApi(page)
  await login(page)

  await page.goto('/settings')
  const loginTabs = page.locator('section[aria-label="登录配置"]')
  const settingsNavigation = page.locator('#settings-navigation')
  await expect(settingsNavigation.getByRole('button')).toHaveCount(3)
  await expect(settingsNavigation.getByRole('button', { name: '短信接口' })).toHaveCount(0)
  await expect(settingsNavigation.getByRole('button', { name: '消息管理' })).toBeVisible()
  await expect(loginTabs.getByRole('tab')).toHaveCount(3)
  await expect(loginTabs.getByRole('tab', { name: '登录基础配置' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('switch', { name: '手机登录' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByRole('switch', { name: '邮箱登录' })).toHaveAttribute('aria-checked', 'true')
  await page.getByRole('switch', { name: '邮箱登录' }).click()
  await page.getByRole('radio', { name: '腾讯云短信' }).check()
  await page.getByRole('button', { name: '保存更改' }).click()
  await expect.poll(() => requests.includes('login-settings:true:false:tencent')).toBe(true)

  await loginTabs.getByRole('tab', { name: '短信配置' }).click()
  await expect(page).toHaveURL(new RegExp('settings[?]section=sms$'))
  const smsTabs = page.getByRole('tablist', { name: '短信配置分类' })
  await expect(smsTabs.getByRole('tab')).toHaveCount(2)
  await expect(smsTabs.getByRole('tab', { name: '短信服务商配置' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('tab', { name: '阿里云配置' })).toHaveAttribute('aria-selected', 'true')
  await page.getByLabel('AccessKey ID').fill('example-access-id')
  await page.getByLabel('AccessKey Secret').fill('example-credential')
  await page.locator('#phone-login-panel-aliyun').getByLabel('短信签名').fill('Atlas')
  await page.getByLabel('登录验证码模板 Code').fill('SMS_123456')
  await page.getByRole('button', { name: '保存更改' }).click()
  await expect.poll(() => requests.includes('aliyun-sms-saved')).toBe(true)

  await page.getByRole('tab', { name: '腾讯云配置' }).click()
  await page.getByLabel('SecretId').fill('example-secret-id')
  await page.getByLabel('SecretKey').fill('example-credential')
  await page.getByLabel('短信 SDK App ID').fill('1400000000')
  await page.locator('#phone-login-panel-tencent').getByLabel('短信签名').fill('Atlas')
  await page.getByLabel('登录验证码模板 ID').fill('123456')
  await page.getByRole('button', { name: '保存更改' }).click()
  await expect.poll(() => requests.includes('tencent-sms-saved')).toBe(true)

  await smsTabs.getByRole('tab', { name: '短信模板管理' }).click()
  await expect(page).toHaveURL(new RegExp('settings[?]section=sms&.*panel=templates$'))
  await expect(page.getByRole('heading', { name: '短信配置' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '短信模板管理' })).toBeVisible()
  await expect(page.getByText('暂无短信模板，请先登记已在服务商控制台创建的模板')).toBeVisible()

  await page.getByRole('button', { name: '消息管理', exact: true }).click()
  await expect(page).toHaveURL(new RegExp('settings/messages$'))
  await expect(page.getByRole('tablist', { name: '通知分类' }).getByRole('tab')).toHaveCount(3)
  await expect(page.getByRole('tab', { name: '会员通知' })).toBeVisible()
  const messageHeaders = page.locator('thead th')
  await expect(messageHeaders).toHaveCount(6)
  await expect(messageHeaders).toHaveText(['ID', '通知类型', '通知场景说明', '站内信', '发送短信', '操作'])
  await expect(page.getByRole('columnheader', { name: '公众号模板' })).toHaveCount(0)
  await expect(page.getByRole('columnheader', { name: '企业微信' })).toHaveCount(0)
  await page.getByRole('tab', { name: '自定义通知' }).click()
  await page.getByRole('button', { name: '新增自定义通知' }).click()
  const channelTabs = page.getByRole('tablist', { name: '通知渠道配置' })
  await expect(channelTabs.getByRole('tab')).toHaveCount(2)
  await expect(channelTabs.getByRole('tab', { name: '站内信通知' })).toBeVisible()
  await expect(channelTabs.getByRole('tab', { name: '短信通知' })).toBeVisible()
  await expect(channelTabs.getByRole('tab', { name: '微信模板消息' })).toHaveCount(0)
  await expect(channelTabs.getByRole('tab', { name: '企业微信' })).toHaveCount(0)
  await page.getByRole('button', { name: '取消' }).click()
  await expect(page.getByRole('tablist', { name: '短信配置分类' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '短信配置' })).toHaveCount(0)

  await page.goto('/settings/sms-interface')
  await expect(page).toHaveURL(new RegExp('settings[?]section=sms$'))
  await expect(page.getByRole('heading', { name: '短信配置' })).toBeVisible()
  await page.goto('/settings/sms-templates')
  await expect(page).toHaveURL(new RegExp('settings[?]section=sms&panel=templates$'))
  await expect(page.getByRole('heading', { name: '短信模板管理' })).toBeVisible()

  await page.goto('/settings')
  await loginTabs.getByRole('tab', { name: '邮箱登录配置' }).click()
  await expect(page).toHaveURL(new RegExp('settings[?]section=email$'))
  await page.getByLabel('SMTP 服务器').fill('smtp.example.com')
  await page.getByLabel('SMTP 用户名').fill('sender@example.com')
  await page.getByLabel('SMTP 密码 / 授权码').fill('example-credential')
  await page.getByLabel('发件邮箱').fill('sender@example.com')
  await page.getByLabel('发件人名称').fill('Atlas')
  await page.getByRole('button', { name: '保存更改' }).click()
  await expect.poll(() => requests.includes('email-smtp-saved')).toBe(true)

  await page.goto('/settings/phone-login')
  await expect(page).toHaveURL(new RegExp('settings[?]section=sms$'))
  const legacyProviderTabs = page.getByRole('tablist', { name: '短信配置分类' })
  await expect(legacyProviderTabs.getByRole('tab', { name: '短信服务商配置' })).toHaveAttribute('aria-selected', 'true')
  await page.goto('/settings/phone-login?tab=tencent')
  await expect(page).toHaveURL(new RegExp('settings[?]tab=tencent&section=sms$'))
  await expect(page.getByRole('tab', { name: '腾讯云配置' })).toHaveAttribute('aria-selected', 'true')
  await page.goto('/settings/email-login')
  await expect(page).toHaveURL(new RegExp('settings[?]section=email$'))
})
test('storage settings render real API state and open OSS binding form', async ({ page }) => {
  await installMockApi(page)
  await login(page)
  await page.goto('/settings/storage')
  await expect(page.getByRole('radio', { name: '本地存储' })).toBeChecked()
  await page.getByRole('tab', { name: '阿里云存储' }).click()
  await expect(page.getByText('暂无数据')).toBeVisible()
  await page.getByRole('button', { name: '添加存储空间' }).click()
  await expect(page.getByPlaceholder('请输入AccessKeyId')).toBeVisible()
  await expect(page.getByPlaceholder('请输入AccessKeySecret')).toHaveAttribute('type', 'password')
  await expect(page.getByRole('radio', { name: '公共读（推荐）' })).toBeChecked()
  await expect(page.getByRole('radio', { name: '公共读写' })).toHaveCount(0)
  await expect(page.getByText('仅支持公共读：访客可查看图片，上传和删除必须通过后台授权。禁止公共读写。')).toBeVisible()
})

test('version panel disables update when the local agent has not been initialized', async ({ page }) => {
  const requests = await installMockApi(page)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('本机更新执行器尚未初始化')).toBeVisible()
  await expect(page.getByRole('button', { name: '已是最新版本' })).toBeDisabled()
  expect(requests).not.toContain('deployment-requested')
})

test('root administrator can update to the latest official GitHub Release', async ({ page }) => {
  const requests = await installMockApi(page, true)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('发现可用的新版本')).toBeVisible()
  await expect(page.getByText('当前版本：v0.1.0 · 最新版本：v0.1.1')).toBeVisible()
  page.once('dialog', async dialog => { await dialog.accept() })
  await page.getByRole('button', { name: '一键更新至 v0.1.1' }).click()
  await expect(page.getByText('正在下载正式版本、构建镜像并检查服务健康状态')).toBeVisible()
  await expect(page.getByRole('button', { name: '更新执行中' })).toBeDisabled()
  expect(requests).toContain('deployment-requested')
})

test('rollback stays disabled without a previous release', async ({ page }) => {
  await installMockApi(page, true)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByRole('button', { name: '回滚上一版本' })).toBeDisabled()
  await expect(page.getByText('没有可回滚的上一版本')).toBeVisible()
})

test('super-admin can request local rollback to the previous recorded release', async ({ page }) => {
  const requests = await installMockApi(page, true, true)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByRole('button', { name: '回滚上一版本' })).toBeEnabled()
  page.once('dialog', async dialog => { await dialog.accept() })
  await page.getByRole('button', { name: '回滚上一版本' }).click()
  await expect(page.getByRole('button', { name: '更新执行中' })).toBeDisabled()
  expect(requests).toContain('rollback-requested')
})

test('super-admin sees human-readable release versions and can restore an archived one', async ({ page }) => {
  const requests = await installMockApi(page, true, true, true)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  const region = page.getByRole('region', { name: '历史版本' })
  await expect(region).toBeVisible()
  await expect(region.getByText('当前版本 · v0.1.0')).toBeVisible()
  await expect(region.getByText('v0.0.8')).toBeVisible()
  await expect(region.getByText('前后端提交 cccccccc')).toBeVisible()
  await expect(region.getByText('前后端提交 eeeeeeee')).toBeVisible()
  await expect(region.getByRole('button', { name: '当前' })).toBeDisabled()
  const choices = region.getByRole('button', { name: '回滚到此版本' })
  await expect(choices).toHaveCount(2)
  page.once('dialog', async dialog => { await dialog.accept() })
  await choices.nth(1).click()
  await expect(page.getByRole('button', { name: '更新执行中' })).toBeDisabled()
  expect(requests).toContain('historical-rollback:' + 'e'.repeat(40) + '_' + 'f'.repeat(40))
})

test('history shows a placeholder before the first customer release', async ({ page }) => {
  await installMockApi(page, true, false, false)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('暂无部署记录，首次上线后将开始记录历史版本。')).toBeVisible()
})

test('no published GitHub Release does not allow an update', async ({ page }) => {
  const requests = await installMockApi(page, true, false, false, 'no-release')
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('GitHub 尚未发布正式版本').first()).toBeVisible()
  await expect(page.getByRole('button', { name: '已是最新版本' })).toBeDisabled()
  expect(requests).not.toContain('deployment-requested')
})

test('a customer already on the latest release cannot update again', async ({ page }) => {
  const requests = await installMockApi(page, true, false, false, 'up-to-date')
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('当前已是最新正式版本')).toBeVisible()
  await expect(page.getByRole('button', { name: '已是最新版本' })).toBeDisabled()
  expect(requests).not.toContain('deployment-requested')
})

test('release notes and official GitHub release link are visible to the administrator', async ({ page }) => {
  await installMockApi(page, true)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await page.getByText('查看 v0.1.1 更新内容').click()
  await expect(page.getByText('修复登录问题并优化系统更新')).toBeVisible()
  await expect(page.getByRole('link', { name: '查看 GitHub 发布说明' }))
    .toHaveAttribute('href', 'https://github.com/wwxst/atlas-console/releases/tag/v0.1.1')
})

test('BaoTa native installations show Java JAR update wording without Docker', async ({ page }) => {
  const requests = await installMockApi(page, true, false, false, 'baota')
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('当前部署方式：宝塔原生（Nginx + Java JAR）')).toBeVisible()
  await expect(page.getByText(/无需 Docker、GitHub PAT 或 Runner/)).toBeVisible()
  page.once('dialog', async dialog => {
    expect(dialog.message()).toContain('切换宝塔静态页面并重启 Java JAR')
    await dialog.accept()
  })
  await page.getByRole('button', { name: '一键更新至 v0.1.1' }).click()
  await expect(page.getByText('正在校验发布文件、切换网站并重启 Java 服务')).toBeVisible()
  expect(requests).toContain('deployment-requested')
})
