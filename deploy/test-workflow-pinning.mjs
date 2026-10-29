import { readFileSync } from 'node:fs'
import { strict as assert } from 'node:assert'
import { test } from 'node:test'

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
const release = readFileSync(new URL('./release.sh', import.meta.url), 'utf8')

test('deployment uses Flyway for a fresh schema and keeps legacy baseline opt-in', () => {
  assert.ok(compose.includes('context: ../backend'))
  assert.ok(compose.includes('dockerfile: Dockerfile.mysql'))
  assert.ok(compose.includes('ATLAS_FLYWAY_BASELINE_ON_MIGRATE: ${ATLAS_FLYWAY_BASELINE_ON_MIGRATE:-false}'))
  assert.ok(!compose.includes('docker-entrypoint-initdb.d'))
  assert.ok(!backend.includes('web_project.sql'))
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
