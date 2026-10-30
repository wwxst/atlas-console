import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'

// Exercise the real CLI before it reads credentials or launches a browser.
// The stub is solely an early-launch tripwire; it never fabricates UI results.
for (const [label, targets, message] of [
  ['empty list', [], 'At least one HTTPS target is required'],
  ['non-array input', {}, 'At least one HTTPS target is required'],
  ['duplicate evidence labels', [{ mode: 'same' }, { mode: 'same' }], 'Evidence labels must be unique'],
]) {
  test(`browser acceptance rejects ${label} before reading credentials`, () => {
    const work = mkdtempSync(path.join(tmpdir(), 'atlas-browser-input-'))
    try {
      const stub = path.join(work, 'frontend', 'node_modules', 'playwright')
      mkdirSync(stub, { recursive: true })
      writeFileSync(path.join(work, 'frontend', 'package.json'), '{}')
      writeFileSync(path.join(stub, 'index.js'), 'exports.chromium = { launch() { throw new Error("Unexpected browser launch") } }')
      mkdirSync(path.join(work, 'deploy'))
      const entry = path.join(work, 'deploy', 'test-live-browser.mjs')
      writeFileSync(entry, readFileSync(new URL('./test-live-browser.mjs', import.meta.url)))
      const input = path.join(work, 'targets.json')
      writeFileSync(input, JSON.stringify(targets))
      const env = { ...process.env }
      delete env.NODE_PATH
      const result = spawnSync(process.execPath, [entry,
        '--targets', input, '--credentials-file', path.join(work, 'must-not-be-read.json'), '--output', path.join(work, 'output')],
      { env, encoding: 'utf8', timeout: 10000 })
      assert.equal(result.status, 1)
      assert(result.stderr.includes(message), 'The CLI must reject invalid targets explicitly')
      assert(!result.stderr.includes('ENOENT'), 'Credentials must not be read')
      assert(!result.stderr.includes('Unexpected browser launch'))
    } finally { rmSync(work, { recursive: true, force: true }) }
  })
}
