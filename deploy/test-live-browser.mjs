// Real browser acceptance against already deployed, independently configured sites.
// No mocked API, HAR, trace or token logging. Only login/logout change server state.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require('playwright')
const args = process.argv.slice(2)
const option = (name) => args[args.indexOf(name) + 1]
for (const name of ['--targets', '--credentials-file', '--output']) {
  assert(args.includes(name) && option(name), `Required option: ${name}`)
}
const targets = JSON.parse(await readFile(option('--targets'), 'utf8'))
assert(Array.isArray(targets) && targets.length > 0, 'At least one HTTPS target is required')
assert.equal(new Set(targets.map(target => target.mode)).size, targets.length, 'Evidence labels must be unique')
const credentials = JSON.parse(await readFile(option('--credentials-file'), 'utf8'))
const output = path.resolve(option('--output'))
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true, channel: process.env.ATLAS_BROWSER_CHANNEL || 'msedge' })
const results = []
try {
  for (const { mode, baseUrl } of targets) {
    assert(/^[a-z-]+$/.test(mode), 'Invalid evidence label')
    const origin = new URL(baseUrl).origin
    assert(origin === baseUrl && origin.startsWith('https://'), 'Use an exact HTTPS origin')
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const page = await context.newPage()
    const errors = []
    const apiFailures = []
    const networkFailures = []
    let apiRequests = 0
    let authenticated = false
    page.on('pageerror', () => errors.push('Uncaught browser JavaScript exception'))
    page.on('console', message => {
      if (message.type() === 'error') errors.push('Browser console error')
    })
    page.on('request', request => {
      const url = new URL(request.url())
      if (url.protocol === 'http:') networkFailures.push('Insecure HTTP request from HTTPS page')
      if (url.pathname.startsWith('/api/') || ['fetch', 'xhr'].includes(request.resourceType())) {
        apiRequests += 1
        if (url.origin !== origin) networkFailures.push('Cross-origin application request')
      }
    })
    page.on('requestfailed', request => networkFailures.push({ path: new URL(request.url()).pathname, error: request.failure()?.errorText }))
    page.on('response', (response) => {
      if (authenticated && new URL(response.url()).pathname.startsWith('/api/') && response.status() >= 400) {
        apiFailures.push({ path: new URL(response.url()).pathname, status: response.status() })
      }
    })
    const record = { mode, status: 'running', scenarios: [] }
    results.push(record)
    const check = async (name, fn) => {
      try { await fn(); record.scenarios.push({ name, status: 'pass' }) }
      catch (error) {
        record.scenarios.push({ name, status: 'fail', reason: error instanceof assert.AssertionError ? 'Browser assertion failed' : 'Browser operation failed' })
        throw error
      }
    }
    const login = async (destination = '/') => {
      await page.getByPlaceholder('请输入登录账号').fill(credentials.username)
      await page.getByPlaceholder('请输入登录密码').fill(credentials.password)
      await page.getByRole('button', { name: '登录', exact: true }).click()
      await page.waitForURL(origin + destination, { timeout: 20000 })
      await page.getByRole('navigation', { name: '主导航' }).waitFor()
      authenticated = true
    }
    try {
      await check('trusted HTTPS login page', async () => {
        const response = await page.goto(origin + '/login')
        assert.equal(response.status(), 200)
        await page.getByPlaceholder('请输入登录账号').waitFor()
        await page.screenshot({ path: path.join(output, `${mode}-login.png`), fullPage: true })
      })
      await check('real administrator login', login)
      for (const [name, route] of [['工作台', '/'], ['普通用户', '/users'], ['系统用户', '/system-users'], ['认证配置', '/settings'], ['存储配置', '/settings/storage']]) {
        await check(`menu and deep refresh: ${name}`, async () => {
          await page.getByRole('navigation', { name: '主导航' }).getByRole('button', { name, exact: true }).click()
          await page.waitForURL(origin + route)
          await page.waitForLoadState('networkidle')
          assert((await page.locator('main').innerText()).trim().length > 10)
          assert.equal(await page.getByText('配置加载失败', { exact: true }).count(), 0)
          await page.reload()
          await page.getByRole('navigation', { name: '主导航' }).waitFor()
          await page.waitForLoadState('networkidle')
          assert.equal(new URL(page.url()).pathname, route)
        })
      }
      await check('storage provider tabs load without external credentials', async () => {
        const tabs = await page.getByRole('tab').all()
        assert(tabs.length > 0, 'No storage provider tabs were rendered')
        for (const tab of tabs) {
          const label = await tab.innerText()
          await tab.click()
          // The router commits the selected tab asynchronously after the click.
          await page.getByRole('tab', { name: label, exact: true, selected: true }).waitFor()
          await page.waitForLoadState('networkidle')
          assert.equal(await tab.getAttribute('aria-selected'), 'true')
        }
      })
      await check('version dialog and disabled unconfigured deploy', async () => {
        await page.getByRole('button', { name: '版本与更新', exact: true }).click()
        await page.getByRole('dialog', { name: '版本与更新' }).waitFor()
        await page.waitForLoadState('networkidle')
        assert(await page.getByRole('button', { name: '一键自动更新', exact: true }).isDisabled())
        await page.getByRole('button', { name: '关闭版本面板' }).click()
      })
      await check('profile placeholder route is explicit', async () => {
        await page.locator('header').getByRole('button', { name: new RegExp(credentials.username) }).click()
        await page.getByRole('menuitem', { name: '个人信息' }).click()
        await page.getByText('个人资料和账号安全设置将在这里展开。', { exact: true }).waitFor()
      })
      await check('logout and protected-route redirect', async () => {
        await page.locator('header').getByRole('button', { name: new RegExp(credentials.username) }).click()
        authenticated = false
        await page.getByRole('menuitem', { name: '退出登录' }).click()
        await page.waitForURL(origin + '/login')
        await page.goto(origin + '/system-users')
        await page.waitForURL(origin + '/login')
      })
      await check('fresh login after logout', async () => {
        await login('/system-users')
        await page.screenshot({ path: path.join(output, `${mode}-authenticated.png`), fullPage: true })
      })
      await check('same-origin API and browser error checks', async () => {
        await page.waitForLoadState('networkidle')
        assert(apiRequests > 0, 'No application API requests were observed')
        assert.deepEqual(errors, [])
        assert.deepEqual(apiFailures, [])
        assert.deepEqual(networkFailures, [])
      })
      record.status = 'pass'
    } catch {
      record.status = 'fail'
      await page.screenshot({ path: path.join(output, `${mode}-failure.png`), fullPage: true }).catch(() => {})
    } finally {
      record.network = structuredClone({ apiRequests, browserErrors: errors, failedApiResponses: apiFailures, networkViolations: networkFailures })
      // Closing this test's page must not turn deliberate teardown cancellations
      // into late failures after the assertions and evidence snapshot.
      for (const event of ['pageerror', 'console', 'request', 'requestfailed', 'response']) page.removeAllListeners(event)
      authenticated = false
      await context.close()
      await writeFile(path.join(output, 'browser-results.json'), JSON.stringify(results, null, 2))
    }
  }
} finally { await browser.close() }
console.log(JSON.stringify(results.map(({ mode, status, scenarios }) => ({ mode, status, checks: scenarios.length, failures: scenarios.filter(s => s.status === 'fail') }))))
if (results.some(result => result.status !== 'pass')) process.exitCode = 1
