import { expect, test, type Page } from '@playwright/test'

const userId = '9007199254740993'
const now = '2026-10-01T12:00:00'

async function installMockApi(page: Page) {
  let nickname = '测试用户'
  let status = 1
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
    if (path === '/api/sys-user/sys-users' && method === 'GET') {
      return reply({ page: 1, pageSize: 20, total: 2, records: [
        { id: 1, username: 'admin', nickname: '测试管理员', avatar: null,
          status: 1, createdAt: now, updatedAt: now },
        { id: 7, username: 'operator', nickname: '测试操作员', avatar: null,
          status: 1, createdAt: now, updatedAt: now },
      ] })
    }
    if (path === '/api/sys-user/users' && method === 'GET') {
      return reply({ page: 1, pageSize: 20, total: 1, records: [{
        id: userId, userNo: '123456', nickname, phone: '13800138000',
        email: 'user@example.com', avatar: null, status, createdAt: now, updatedAt: now,
      }] })
    }
    if (path === `/api/sys-user/users/${userId}` && method === 'PUT') {
      const data = route.request().postDataJSON() as { nickname: string; phone: string | null; email: string | null }
      nickname = data.nickname
      requests.push('edit:' + userId)
      return reply({ id: userId, userNo: '123456', nickname, phone: data.phone,
        email: data.email, avatar: null, status, createdAt: now, updatedAt: now })
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
  await expect(page.locator('thead th').last()).toHaveCSS('text-align', 'right')
  const userRow = page.getByRole('row', { name: /测试用户/ })
  await expect(userRow.locator('td').nth(1).locator('span').first()).toBeVisible()
  await expect(userRow.locator('td').nth(2)).toHaveText('测试用户')
  await expect(userRow.locator('td').last()).toHaveCSS('text-align', 'right')
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

test('system administrators show their existing ID as 编号 in the list and detail view', async ({ page }) => {
  await installMockApi(page)
  await login(page)
  await page.goto('/system-users')
  await expect(page.locator('thead th').first()).toHaveText('编号')
  await expect(page.locator('thead th').nth(1)).toHaveText('头像')
  await expect(page.locator('thead th').nth(2)).toHaveText('昵称')
  await expect(page.locator('thead th').last()).toHaveCSS('text-align', 'right')
  const adminRow = page.getByRole('row', { name: /测试管理员/ })
  await expect(adminRow.locator('td').first()).toHaveText('1')
  await expect(adminRow.locator('td').nth(1).locator('span').first()).toBeVisible()
  await expect(adminRow.locator('td').nth(2)).toHaveText('测试管理员')
  await expect(adminRow.locator('td').last()).toHaveCSS('text-align', 'right')
  await expect(page.getByRole('row', { name: /测试操作员/ }).locator('td').first()).toHaveText('7')
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
})
