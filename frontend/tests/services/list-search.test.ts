import assert from 'node:assert/strict'
import test from 'node:test'
import { withListKeyword } from '../../src/services/listSearch.ts'

test('global search writes the keyword parameter consumed by user lists', () => {
  const params = new URLSearchParams(withListKeyword('?status=1&q=old', '  alice  '))
  assert.equal(params.get('keyword'), 'alice')
  assert.equal(params.get('q'), null)
  assert.equal(params.get('status'), '1')
})

test('clearing global search removes both search parameter spellings', () => {
  assert.equal(withListKeyword('?q=old&keyword=alice', '  '), '')
})

test('submitting and clearing global search reset pagination while keeping list filters', () => {
  for (const keyword of ['alice', '  ']) {
    const params = new URLSearchParams(withListKeyword('?page=3&pageSize=20&status=1&createdAtOrder=asc&keyword=old', keyword))
    assert.equal(params.get('page'), null)
    assert.equal(params.get('pageSize'), '20')
    assert.equal(params.get('status'), '1')
    assert.equal(params.get('createdAtOrder'), 'asc')
    assert.equal(params.get('keyword'), keyword.trim() || null)
  }
})
