import { readFileSync } from 'node:fs'
import { strict as assert } from 'node:assert'
import { test } from 'node:test'

// Prevent a verified backend A from becoming an unverified backend B when
// main advances between the test job and the production runner checkout.
const yaml = readFileSync(new URL('../.github/workflows/deploy-production.yml', import.meta.url), 'utf8')
function section(start, end) {
  const from = yaml.indexOf('\n  ' + start + ':')
  const until = end ? yaml.indexOf('\n  ' + end + ':', from + 1) : yaml.length
  assert.ok(from !== -1 && until !== -1 && until > from, 'missing workflow job: ' + start)
  return yaml.slice(from, until)
}
const frontend = section('verify-frontend', 'verify-backend')
const backend = section('verify-backend', 'deploy')
const deploy = section('deploy')

test('frontend verification and deployment use workflow event SHA', () => {
  assert.match(frontend, /actions\/checkout@v4\s+with:\s+ref: \$\{\{ github\.sha \}\}/)
  assert.match(deploy, /actions\/checkout@v4\s+with:\s+ref: \$\{\{ github\.sha \}\}\s+path: atlas-console/)
})

test('backend exports precisely the commit that was tested', () => {
  assert.match(backend, /backend_sha: \$\{\{ steps\.revision\.outputs\.sha \}\}/)
  assert.match(backend, /id: revision/)
  assert.match(backend, /commit="\$\(git rev-parse HEAD\)"/)
  assert.match(backend, /printf 'sha=%s\\n' "\$commit" >> "\$GITHUB_OUTPUT"/)
})

test('production backend checkout uses verified SHA rather than moving main', () => {
  assert.match(deploy, /if: inputs\.operation == 'deploy'\s+with:\s+repository: wwxst\/backend\s+ref: \$\{\{ needs\.verify-backend\.outputs\.backend_sha \}\}/)
  assert.doesNotMatch(deploy, /ref: main\b/)
  assert.match(deploy, /test "\$\(git -C backend rev-parse HEAD\)" = "\$VERIFIED_BACKEND_SHA"/)
  assert.match(deploy, /test "\$\(git -C atlas-console rev-parse HEAD\)" = "\$VERIFIED_FRONTEND_SHA"/)
})

test('historical and previous-release rollback do not require a tested backend checkout', () => {
  assert.match(backend, /if: inputs\.operation == 'deploy'/)
  assert.match(deploy, /inputs\.operation == 'rollback-history'/)
  assert.match(deploy, /RELEASE_OPERATION: \$\{\{ inputs\.operation \}\}/)
})
