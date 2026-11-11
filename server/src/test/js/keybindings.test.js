import { test } from 'node:test';
import assert from 'node:assert';
import { parseBinding, tokenMatches } from '../../main/resources/static/modules/keybindings.js';

// ── parseBinding ──────────────────────────────────────

test('parseBinding: single character', () => {
  assert.deepStrictEqual(parseBinding('g'), [{ key: 'g' }]);
});

test('parseBinding: multi-char sequence (gg)', () => {
  assert.deepStrictEqual(parseBinding('gg'), [{ key: 'g' }, { key: 'g' }]);
});

test('parseBinding: Ctrl+key shorthand (C-x)', () => {
  assert.deepStrictEqual(parseBinding('C-x'), [{ mod: 'ctrl', key: 'x' }]);
});

test('parseBinding: Alt+key shorthand (M-f)', () => {
  assert.deepStrictEqual(parseBinding('M-f'), [{ mod: 'alt', key: 'f' }]);
});

test('parseBinding: explicit Ctrl+key', () => {
  assert.deepStrictEqual(parseBinding('Ctrl+s'), [{ mod: 'ctrl', key: 's' }]);
});

test('parseBinding: multi-modifier Ctrl+Shift+F', () => {
  assert.deepStrictEqual(parseBinding('Ctrl+Shift+F'), [{ mod: 'ctrl+shift', key: 'f' }]);
});

test('parseBinding: multi-modifier Ctrl+Alt+B', () => {
  assert.deepStrictEqual(parseBinding('Ctrl+Alt+B'), [{ mod: 'ctrl+alt', key: 'b' }]);
});

test('parseBinding: bracket sequence [d]', () => {
  assert.deepStrictEqual(parseBinding('[d]'), [{ key: '[' }, { key: 'd' }, { key: ']' }]);
});

test('parseBinding: mixed modifier and sequence (C-x gg)', () => {
  assert.deepStrictEqual(parseBinding('C-x gg'), [
    { mod: 'ctrl', key: 'x' },
    { key: 'g' },
    { key: 'g' },
  ]);
});

test('parseBinding: uppercase key is lowercased', () => {
  assert.deepStrictEqual(parseBinding('G'), [{ key: 'g' }]);
});

test('parseBinding: space-separated tokens', () => {
  assert.deepStrictEqual(parseBinding('d d'), [{ key: 'd' }, { key: 'd' }]);
});

// ── tokenMatches ──────────────────────────────────────

function makeEvent({ key, ctrlKey = false, shiftKey = false, altKey = false, metaKey = false }) {
  return { key, ctrlKey, shiftKey, altKey, metaKey };
}

test('tokenMatches: plain key match', () => {
  assert.ok(tokenMatches({ key: 'g' }, makeEvent({ key: 'g' })));
});

test('tokenMatches: plain key mismatch', () => {
  assert.ok(!tokenMatches({ key: 'g' }, makeEvent({ key: 'h' })));
});

test('tokenMatches: plain key rejects when modifier pressed', () => {
  assert.ok(!tokenMatches({ key: 'g' }, makeEvent({ key: 'g', ctrlKey: true })));
});

test('tokenMatches: ctrl modifier match', () => {
  assert.ok(tokenMatches({ mod: 'ctrl', key: 's' }, makeEvent({ key: 's', ctrlKey: true })));
});

test('tokenMatches: ctrl modifier missing', () => {
  assert.ok(!tokenMatches({ mod: 'ctrl', key: 's' }, makeEvent({ key: 's' })));
});

test('tokenMatches: shift modifier match', () => {
  assert.ok(tokenMatches({ mod: 'shift', key: 'f' }, makeEvent({ key: 'f', shiftKey: true })));
});

test('tokenMatches: multi-modifier all present', () => {
  assert.ok(tokenMatches(
    { mod: 'ctrl+shift', key: 'f' },
    makeEvent({ key: 'f', ctrlKey: true, shiftKey: true })
  ));
});

test('tokenMatches: multi-modifier one missing', () => {
  assert.ok(!tokenMatches(
    { mod: 'ctrl+shift', key: 'f' },
    makeEvent({ key: 'f', ctrlKey: true })
  ));
});

test('tokenMatches: alt modifier match', () => {
  assert.ok(tokenMatches({ mod: 'alt', key: 'b' }, makeEvent({ key: 'b', altKey: true })));
});

test('tokenMatches: meta modifier match', () => {
  assert.ok(tokenMatches({ mod: 'meta', key: 'k' }, makeEvent({ key: 'k', metaKey: true })));
});

