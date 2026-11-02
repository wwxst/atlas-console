import { readFileSync } from 'node:fs'
import { strict as assert } from 'node:assert'
import { test } from 'node:test'

const releaseWorkflow = readFileSync(new URL('../.github/workflows/release.yml', import.meta.url), 'utf8')
const compose = readFileSync(new URL('./compose.yaml', import.meta.url), 'utf8')
const release = readFileSync(new URL('./release.sh', import.meta.url), 'utf8')
const installer = readFileSync(new URL('../install.sh', import.meta.url), 'utf8')
const agent = readFileSync(new URL('./updater/server.py', import.meta.url), 'utf8')
const worker = readFileSync(new URL('./updater/worker.py', import.meta.url), 'utf8')
const dockerfile = readFileSync(new URL('./updater/Dockerfile', import.meta.url), 'utf8')

test('GitHub Releases are created only from semantic-version tags', () => {
  assert.ok(releaseWorkflow.includes('tags:'))
  assert.ok(releaseWorkflow.includes('- "v*"'))
  assert.ok(releaseWorkflow.includes('GITHUB_REF_NAME'))
  assert.ok(releaseWorkflow.includes('frontend/package.json'))
  assert.ok(releaseWorkflow.includes('git merge-base --is-ancestor "$GITHUB_SHA" origin/main'))
  assert.ok(releaseWorkflow.includes('gh release create'))
  assert.ok(releaseWorkflow.includes('contents: write'))
  assert.ok(!releaseWorkflow.includes('self-hosted'))
})

test('customer updates use public Releases and a local private updater, not a GitHub Runner', () => {
  assert.ok(agent.includes('/releases/latest'))
  assert.ok(agent.includes('hmac.compare_digest'))
  assert.ok(agent.includes('docker", "run", "--detach"'))
  assert.ok(agent.includes('atlas-updater:local'))
  assert.ok(agent.includes('atlas-update-worker-'))
  assert.ok(!agent.includes('Authorization: token'))
  assert.ok(!agent.includes('shell=True'))
  assert.ok(!worker.includes('shell=True'))
  assert.ok(worker.includes('git", "clone"'))
  assert.ok(worker.includes('deploy", "rollback", "rollback-history'))
  assert.ok(worker.includes('stage / "frontend" / "package.json"'))
  assert.ok(dockerfile.includes('docker-cli-compose'))
  assert.ok(dockerfile.includes('git'))
})

test('only the updater agent receives the Docker socket', () => {
  const updater = compose.slice(compose.indexOf('  updater:'), compose.indexOf('  backend:'))
  const backend = compose.slice(compose.indexOf('  backend:'), compose.indexOf('  frontend:'))
  assert.ok(updater.includes('/var/run/docker.sock:/var/run/docker.sock'))
  assert.ok(updater.includes('ATLAS_DEPLOYMENT_AGENT_TOKEN'))
  assert.ok(updater.includes('8090'))
  assert.ok(!updater.includes('ports:'))
  assert.ok(backend.includes('ATLAS_DEPLOYMENT_AGENT_URL: http://updater:8090'))
  assert.ok(backend.includes('ATLAS_DEPLOYMENT_AGENT_TOKEN'))
  assert.ok(!backend.includes('/var/run/docker.sock'))
  assert.ok(backend.includes('condition: service_healthy'))
})

test('release activation builds the isolated updater and health-checks the stack', () => {
  assert.ok(release.includes('compose build frontend backend updater'))
  assert.ok(release.includes('compose up --detach --no-build --wait'))
  assert.ok(release.includes('Deployment failed health verification'))
  assert.ok(release.includes('Previous running application version restored automatically'))
  assert.ok(!release.includes('ATLAS_DEPLOYMENT_GITHUB_TOKEN'))
})

test('installer creates a unique per-install token and enables customer updates by default', () => {
  assert.ok(installer.includes('ATLAS_DEPLOYMENT_AGENT_TOKEN="$(openssl rand -hex 32)"'))
  assert.ok(installer.includes('write_env ATLAS_DEPLOYMENT_ENABLED true'))
  assert.ok(installer.includes('write_env ATLAS_DEPLOYMENT_AGENT_TOKEN "$ATLAS_DEPLOYMENT_AGENT_TOKEN"'))
  assert.ok(!installer.includes('ATLAS_DEPLOYMENT_GITHUB_TOKEN'))
  assert.ok(!installer.includes('register a GitHub Runner'))
})

test('database remains persistent and isolated from the public internet', () => {
  const postgresCompose = readFileSync(new URL('./compose.postgres.yaml', import.meta.url), 'utf8')
  assert.ok(compose.includes('context: ../backend'))
  assert.ok(compose.includes('context: ../frontend'))
  assert.ok(postgresCompose.includes('image: postgres:17-alpine'))
  assert.ok(compose.includes('ATLAS_FLYWAY_BASELINE_ON_MIGRATE: ${ATLAS_FLYWAY_BASELINE_ON_MIGRATE:-false}'))
  assert.ok(postgresCompose.includes('docker-entrypoint-initdb.d/10-create-app-role.sh'))
  assert.ok(!postgresCompose.includes('ports:'))
  assert.ok(compose.includes('127.0.0.1:${ATLAS_HTTP_PORT:-8088}:8080'))
})

test('frontend CI validates the customer updater Compose configuration and Python tests', () => {
  const ci = readFileSync(new URL('../.github/workflows/frontend-quality.yml', import.meta.url), 'utf8')
  assert.ok(ci.includes('cache-dependency-path: frontend/package-lock.json'))
  assert.ok(ci.includes('working-directory: frontend'))
  assert.ok(ci.includes("python3 -m unittest discover -s atlas-console/deploy/updater"))
  assert.ok(ci.includes('atlas-console/deploy/compose.yaml'))
  assert.ok(ci.includes('ATLAS_DEPLOYMENT_AGENT_TOKEN'))
})
