import { test } from 'node:test';
import assert from 'node:assert';
import { pushHistory, getHistoryState } from '../../main/resources/static/modules/nav-history.js';

// Note: nav-history uses module-level state, so tests are sequential.
// Each test asserts on the state AFTER its pushHistory call.

test('pushHistory: first entry', () => {
  pushHistory('src/A.java');
  const { history, index } = getHistoryState();
  assert.ok(history.includes('src/A.java'));
  assert.strictEqual(index, history.length - 1);
});

test('pushHistory: second entry', () => {
  pushHistory('src/B.java');
  const { history, index } = getHistoryState();
  assert.ok(history.includes('src/B.java'));
  assert.strictEqual(index, history.length - 1);
});

test('pushHistory: skips consecutive duplicate', () => {
  const { history: before } = getHistoryState();
  const lastPath = before[before.length - 1];
  pushHistory(lastPath); // same as current
  const { history, index } = getHistoryState();
  assert.strictEqual(history.length, before.length);
  assert.strictEqual(index, before.length - 1);
});

test('pushHistory: ignores empty/falsy path', () => {
  const { history: before } = getHistoryState();
  pushHistory('');
  pushHistory(null);
  pushHistory(undefined);
  const { history } = getHistoryState();
  assert.strictEqual(history.length, before.length);
});

test('pushHistory: non-consecutive repeat is allowed', () => {
  pushHistory('src/C.java');
  pushHistory('src/A.java'); // was first entry, not consecutive
  const { history } = getHistoryState();
  const aCount = history.filter(p => p === 'src/A.java').length;
  assert.ok(aCount >= 2);
});

test('getHistoryState: returns copy (mutation-safe)', () => {
  const state1 = getHistoryState();
  state1.history.push('hacked');
  const state2 = getHistoryState();
  assert.ok(!state2.history.includes('hacked'));
});

