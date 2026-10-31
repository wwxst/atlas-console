import { expect, test, type Page } from '@playwright/test'

const userId = '9007199254740993'
const now = '2026-10-01T12:00:00'

async function installMockApi(page: Page, deploymentReady = false, previousRelease = false,
  releaseHistory = false, scenario: 'normal' | 'missing' | 'stalled' = 'normal') {
  let deploymentRequested = false
  let recoveryCompleted = false
  let nickname = '测试用户'
  let status = 1
  let systemUserStatus = 1
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
      const needsRecovery = deploymentRequested && !recoveryCompleted && scenario !== 'normal'
      return reply({ configured: deploymentReady,
        state: !deploymentReady ? 'not_configured' : recoveryCompleted ? 'not_started'
          : needsRecovery ? scenario === 'missing' ? 'timed_out' : 'stalled'
          : deploymentRequested ? 'queued' : 'not_started',
        conclusion: null,
        runUrl: deploymentRequested ? 'https://github.com/wwxst/atlas-console/actions/runs/123' : null,
        title: deploymentRequested ? 'Atlas deploy mock-request' : null,
        requestId: deploymentRequested && !recoveryCompleted ? 'mock-request' : null,
        runId: deploymentRequested && scenario === 'stalled' ? 123 : null,
        recoveryAvailable: needsRecovery, rollbackAvailable: previousRelease })
    }
    if (path === '/api/sys-user/deployment/recover' && method === 'POST') {
      requests.push('recovery-requested')
      if (scenario === 'stalled') {
        return route.fulfill({ status: 200, contentType: 'application/json',
          body: JSON.stringify({ code: 400, msg: 'GitHub 仍有排队或运行中的任务', data: null }) })
      }
      recoveryCompleted = true
      return reply({ configured: true, state: 'not_started', conclusion: null,
        runUrl: null, title: null, requestId: null, runId: null,
        recoveryAvailable: false, rollbackAvailable: previousRelease })
    }
    if (path === '/api/sys-user/deployment/history' && method === 'GET') {
      const a = 'a'.repeat(40)
      const b = 'b'.repeat(40)
      const c = 'c'.repeat(40)
      const d = 'd'.repeat(40)
      const e = 'e'.repeat(40)
      const f = 'f'.repeat(40)
      return reply(releaseHistory ? [
        { releaseId: c + '_' + d, frontendSha: c, backendSha: d, deployedAt: '2026-10-28T10:00:00Z', current: true },
        { releaseId: a + '_' + b, frontendSha: a, backendSha: b, deployedAt: '2026-10-27T10:00:00Z', current: false },
        { releaseId: e + '_' + f, frontendSha: e, backendSha: f, deployedAt: '2026-10-26T10:00:00Z', current: false },
      ] : [])
    }
    if (path === '/api/sys-user/deployment/rollback/history' && method === 'POST') {
      const data = route.request().postDataJSON() as { releaseId: string }
      deploymentRequested = true
      requests.push('historical-rollback:' + data.releaseId)
      return reply({ configured: true, state: 'requested', conclusion: null,
        runUrl: 'https://github.com/wwxst/atlas-console/actions/workflows/deploy-production.yml',
        title: 'Atlas rollback-history mock-request', requestId: 'mock-request', rollbackAvailable: true })
    }
    if (path === '/api/sys-user/deployment/rollback' && method === 'POST') {
      deploymentRequested = true
      requests.push('rollback-requested')
      return reply({ configured: true, state: 'requested', conclusion: null,
        runUrl: 'https://github.com/wwxst/atlas-console/actions/workflows/deploy-production.yml',
        title: 'Atlas rollback mock-request', requestId: 'mock-request', rollbackAvailable: true })
    }
    if (path === '/api/sys-user/deployment' && method === 'POST') {
      deploymentRequested = true
      requests.push('deployment-requested')
      return reply({ configured: true, state: 'requested', conclusion: null,
        runUrl: 'https://github.com/wwxst/atlas-console/actions/workflows/deploy-production.yml',
        title: 'Atlas Deploy mock-request', requestId: 'mock-request' })
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

test('version panel keeps one-click update disabled until production is configured', async ({ page }) => {
  const requests = await installMockApi(page)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('尚未配置生产服务器，暂不能执行更新')).toBeVisible()
  await expect(page.getByRole('button', { name: '一键自动更新' })).toBeDisabled()
  expect(requests).not.toContain('deployment-requested')
})

test('root administrator may request a verified GitHub deployment', async ({ page }) => {
  const requests = await installMockApi(page, true)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('尚无部署记录')).toBeVisible()
  page.once('dialog', async (dialog) => { await dialog.accept() })
  await page.getByRole('button', { name: '一键自动更新' }).click()
  await expect(page.getByText('正在等待或执行 GitHub Actions 部署')).toBeVisible()
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

test('super-admin can request rollback to the previous recorded release', async ({ page }) => {
  const requests = await installMockApi(page, true, true)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByRole('button', { name: '回滚上一版本' })).toBeEnabled()
  page.once('dialog', async dialog => { await dialog.accept() })
  await page.getByRole('button', { name: '回滚上一版本' }).click()
  await expect(page.getByRole('button', { name: '更新执行中' })).toBeDisabled()
  expect(requests).toContain('rollback-requested')
})

test('super-admin sees historical version pairs and can select a non-previous release', async ({ page }) => {
  const requests = await installMockApi(page, true, true, true)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByRole('region', { name: '历史版本' })).toBeVisible()
  await expect(page.getByText('前端 cccccccc · 后端 dddddddd')).toBeVisible()
  await expect(page.getByText('前端 eeeeeeee · 后端 ffffffff')).toBeVisible()
  const region = page.getByRole('region', { name: '历史版本' })
  await expect(region.getByRole('button', { name: '当前' })).toBeDisabled()
  const choices = region.getByRole('button', { name: '回滚到此版本' })
  await expect(choices).toHaveCount(2)
  page.once('dialog', async dialog => { await dialog.accept() })
  await choices.nth(1).click()
  await expect(page.getByRole('button', { name: '更新执行中' })).toBeDisabled()
  expect(requests).toContain('historical-rollback:' + 'e'.repeat(40) + '_' + 'f'.repeat(40))
})

test('history shows an empty state until the first production deployment', async ({ page }) => {
  await installMockApi(page, true, false, false)
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  await expect(page.getByText('暂无部署记录，首次上线后将开始记录历史版本。')).toBeVisible()
})

test('missing GitHub execution terminates waiting and can be recovered manually', async ({ page }) => {
  const requests = await installMockApi(page, true, true, false, 'missing')
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  page.once('dialog', async dialog => { await dialog.accept() })
  await page.getByRole('button', { name: '一键自动更新' }).click()
  await expect(page.getByText('等待超过 5 分钟，仍未找到本次执行记录，请核实后恢复')).toBeVisible()
  await expect(page.getByRole('button', { name: '需先恢复状态' })).toBeDisabled()
  page.once('dialog', async dialog => { await dialog.accept() })
  await page.getByRole('button', { name: '核实并恢复更新状态' }).click()
  await expect(page.getByText('尚无部署记录')).toBeVisible()
  await expect(page.getByRole('button', { name: '一键自动更新' })).toBeEnabled()
  expect(requests).toContain('recovery-requested')
})

test('stalled GitHub execution remains protected from unsafe recovery', async ({ page }) => {
  const requests = await installMockApi(page, true, true, false, 'stalled')
  await login(page)
  await page.getByRole('button', { name: '版本与更新' }).click()
  page.once('dialog', async dialog => { await dialog.accept() })
  await page.getByRole('button', { name: '一键自动更新' }).click()
  await expect(page.getByText('任务超过 1 小时仍未结束，请检查 GitHub Runner 或取消后恢复')).toBeVisible()
  page.once('dialog', async dialog => { await dialog.accept() })
  await page.getByRole('button', { name: '核实并恢复更新状态' }).click()
  await expect(page.getByText('GitHub 仍有排队或运行中的任务').first()).toBeVisible()
  await expect(page.getByRole('button', { name: '需先恢复状态' })).toBeDisabled()
  expect(requests).toContain('recovery-requested')
})
