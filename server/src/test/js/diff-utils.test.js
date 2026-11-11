import { test } from 'node:test';
import assert from 'node:assert';

// Minimal DOM shim so wordDiffHighlight can run in Node
globalThis.document = {
  createElement(tag) {
    return { set textContent(v) { this._text = v; }, get innerHTML() { return this._text?.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') ?? ''; } };
  }
};

import { tokenize, computeLCS, wordDiffHighlight } from '../../main/resources/static/modules/diff-utils.js';

test('tokenize: empty string', () => {
  assert.deepStrictEqual(tokenize(''), []);
});

test('tokenize: single word', () => {
  assert.deepStrictEqual(tokenize('hello'), ['hello']);
});

test('tokenize: words and spaces', () => {
  assert.deepStrictEqual(tokenize('hello world'), ['hello', ' ', 'world']);
});

test('tokenize: special characters', () => {
  assert.deepStrictEqual(tokenize('a+b=c'), ['a', '+', 'b', '=', 'c']);
});

test('tokenize: mixed whitespace', () => {
  assert.deepStrictEqual(tokenize('a  b\tc'), ['a', '  ', 'b', '\t', 'c']);
});

test('computeLCS: identical arrays', () => {
  assert.deepStrictEqual(computeLCS([1,2,3], [1,2,3]), [1,2,3]);
});

test('computeLCS: disjoint arrays', () => {
  assert.deepStrictEqual(computeLCS([1,2], [3,4]), []);
});

test('computeLCS: partial overlap', () => {
  assert.deepStrictEqual(computeLCS([1,2,3,4], [2,3]), [2,3]);
});

test('computeLCS: empty vs non-empty', () => {
  assert.deepStrictEqual(computeLCS([], [1,2,3]), []);
});

test('computeLCS: single common element', () => {
  assert.deepStrictEqual(computeLCS([1,2,3], [3,4,5]), [3]);
});

test('wordDiffHighlight: identical strings', () => {
  const result = wordDiffHighlight('hello world', 'hello world');
  assert.ok(!result.includes('<ins'));
});

test('wordDiffHighlight: one word changed', () => {
  const result = wordDiffHighlight('hello world', 'hello earth');
  assert.ok(result.includes('<ins class="word-diff">earth</ins>'));
  assert.ok(!result.includes('<ins class="word-diff">world</ins>'));
});

test('wordDiffHighlight: empty old string', () => {
  const result = wordDiffHighlight('', 'abc');
  assert.ok(result.includes('<ins'));
});

