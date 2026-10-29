import { readFileSync } from 'node:fs'
import { strict as assert } from 'node:assert'
import { test } from 'node:test'
import { runInNewContext } from 'node:vm'

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

// Evaluate the actual job condition so cancellation fixes cannot accidentally
// disable rollback when its verification dependencies are skipped.
const deployCondition = deploy.match(/    if: >-\r?\n([\s\S]*?)    runs-on:/)[1]
  .trim().replace(/^\$\{\{\s*|\s*\}\}$/g, '')
  .replace(/needs\.([\w-]+)\.result/g, "needs['$1'].result")
function canDeploy({ operation = 'deploy', ref = 'refs/heads/main', cancelled = false,
  frontendResult = 'success', backendResult = 'success' } = {}) {
  return runInNewContext(deployCondition, {
    always: () => true,
    cancelled: () => cancelled,
    github: { ref },
    inputs: { operation },
    needs: { 'verify-frontend': { result: frontendResult }, 'verify-backend': { result: backendResult } },
  }, { timeout: 1000 })
}

test('cancelling a run prevents deployment and both rollback operations', () => {
  for (const operation of ['deploy', 'rollback', 'rollback-history']) {
    assert.equal(canDeploy({ operation, cancelled: true }), false, operation)
  }
})

test('deploy requires successful frontend and backend verification', () => {
  assert.equal(canDeploy(), true)
  for (const result of ['failure', 'skipped', 'cancelled']) {
    assert.equal(canDeploy({ frontendResult: result }), false, 'frontend: ' + result)
    assert.equal(canDeploy({ backendResult: result }), false, 'backend: ' + result)
  }
})

test('rollback can run with skipped verification jobs unless the run is cancelled', () => {
  for (const operation of ['rollback', 'rollback-history']) {
    const skipped = { operation, frontendResult: 'skipped', backendResult: 'skipped' }
    assert.equal(canDeploy(skipped), true, operation)
    assert.equal(canDeploy({ ...skipped, cancelled: true }), false, operation + ' cancelled')
  }
})

test('all production operations reject branches other than main', () => {
  for (const operation of ['deploy', 'rollback', 'rollback-history']) {
    assert.equal(canDeploy({ operation, ref: 'refs/heads/fix/example' }), false, operation)
  }
})

test('frontend verification and deployment use the workflow event SHA', () => {
  assert.ok(frontend.includes('ref: ${{ github.sha }}'))
  assert.ok(deploy.includes('ref: ${{ github.sha }}'))
  assert.ok(deploy.includes('path: atlas-console'))
})

test('backend verification checks backend/ from the same repository commit', () => {
  assert.ok(backend.includes('ref: ${{ github.sha }}'))
  assert.ok(backend.includes('backend_sha: ${{ steps.revision.outputs.sha }}'))
  assert.ok(backend.includes('commit="$(git rev-parse HEAD)"'))
  assert.ok(backend.includes('working-directory: backend'))
  assert.ok(backend.includes('cache-dependency-path: backend/pom.xml'))
  assert.ok(!backend.includes('repository: wwxst/backend'))
})

test('production deployment checks out one commit and requires both image revisions to match it', () => {
  assert.ok(deploy.includes('test "$(git -C atlas-console rev-parse HEAD)" = "$VERIFIED_FRONTEND_SHA"'))
  assert.ok(deploy.includes('test "$VERIFIED_FRONTEND_SHA" = "$VERIFIED_BACKEND_SHA"'))
  assert.ok(deploy.includes('test -f atlas-console/backend/pom.xml'))
  assert.ok(!deploy.includes('repository: wwxst/backend'))
})

test('rollback operations do not require verification jobs to run', () => {
  assert.ok(backend.includes("if: inputs.operation == 'deploy'"))
  assert.ok(deploy.includes("inputs.operation == 'rollback-history'"))
  assert.ok(deploy.includes('RELEASE_OPERATION: ${{ inputs.operation }}'))
})

const compose = readFileSync(new URL('./compose.yaml', import.meta.url), 'utf8')
const mysqlCompose = readFileSync(new URL('./compose.mysql.yaml', import.meta.url), 'utf8')
const release = readFileSync(new URL('./release.sh', import.meta.url), 'utf8')

test('deployment uses Flyway for a fresh schema and keeps legacy baseline opt-in', () => {
  assert.ok(compose.includes('context: ../backend'))
  assert.ok(mysqlCompose.includes('dockerfile: Dockerfile.mysql'))
  assert.ok(compose.includes('ATLAS_FLYWAY_BASELINE_ON_MIGRATE: ${ATLAS_FLYWAY_BASELINE_ON_MIGRATE:-false}'))
  assert.ok(!mysqlCompose.includes('docker-entrypoint-initdb.d'))
  assert.ok(!backend.includes('web_project.sql'))
})

test('deployment supports bundled MySQL and an existing external database', () => {
  assert.ok(compose.includes('ATLAS_DB_URL'))
  assert.ok(compose.includes('host.docker.internal:host-gateway'))
  assert.ok(mysqlCompose.includes('mysql:3306/web_project'))
  assert.ok(release.includes('db_mode'))
  assert.ok(release.includes('external) compose_args=(-f "$compose_file")'))
  assert.ok(release.includes('docker) compose_args=(-f "$compose_file" -f "$mysql_compose_file")'))
})

const ci = readFileSync(new URL('../.github/workflows/frontend-quality.yml', import.meta.url), 'utf8')
test('deployment-config validates Compose from the single monorepo checkout', () => {
  const start = ci.indexOf('  deployment-config:')
  assert.ok(start !== -1)
  const section = ci.slice(start)
  assert.ok(!section.includes('repository: wwxst/backend'))
  assert.ok(section.includes('atlas-console/deploy/compose.yaml'))
})

test('release script validates one monorepo SHA and does not require a second checkout', () => {
  assert.ok(release.includes('git -C "$project_root" rev-parse HEAD'))
  assert.ok(release.includes('"$VERIFIED_FRONTEND_SHA" != "$VERIFIED_BACKEND_SHA"'))
  assert.ok(!release.includes('workspace/backend/.git'))
})
