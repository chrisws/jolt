import { test } from 'node:test';
import assert from 'node:assert';
import { buildSearchUrl, parseRoute, getShareableUrl } from '../../main/resources/static/modules/url-utils.js';

// ── buildSearchUrl ──────────────────────────────────

test('buildSearchUrl: basic query', () => {
  assert.strictEqual(buildSearchUrl('foo'), '/api/search?q=foo');
});

test('buildSearchUrl: semantic mode', () => {
  assert.strictEqual(buildSearchUrl('foo', { semantic: true }), '/api/search/semantic?q=foo');
});

test('buildSearchUrl: case-sensitive flag', () => {
  assert.strictEqual(buildSearchUrl('foo', { caseSensitive: true }), '/api/search?q=foo&cs=1');
});

test('buildSearchUrl: whole-word flag', () => {
  assert.strictEqual(buildSearchUrl('foo', { wholeWord: true }), '/api/search?q=foo&ww=1');
});

test('buildSearchUrl: all flags combined', () => {
  const url = buildSearchUrl('bar', { semantic: true, caseSensitive: true, wholeWord: true });
  assert.strictEqual(url, '/api/search/semantic?q=bar&cs=1&ww=1');
});

test('buildSearchUrl: URL-encodes special chars', () => {
  assert.strictEqual(buildSearchUrl('a b+c'), '/api/search?q=a%20b%2Bc');
});

// ── parseRoute ──────────────────────────────────────

test('parseRoute: empty hash → file', () => {
  assert.deepStrictEqual(parseRoute(''), { page: 'file' });
});

test('parseRoute: #/ → file', () => {
  assert.deepStrictEqual(parseRoute('#/'), { page: 'file' });
});

test('parseRoute: #/dashboard', () => {
  assert.deepStrictEqual(parseRoute('#/dashboard'), { page: 'dashboard' });
});

test('parseRoute: #/git → stats', () => {
  assert.deepStrictEqual(parseRoute('#/git'), { page: 'git', view: 'stats' });
});

test('parseRoute: #/git/commits → commit list', () => {
  assert.deepStrictEqual(parseRoute('#/git/commits'), { page: 'git', view: 'commits', branch: [] });
});

test('parseRoute: #/git/commits/main → commit detail (hash)', () => {
  assert.deepStrictEqual(parseRoute('#/git/commits/main'), { page: 'git', view: 'commit', hash: 'main' });
});

test('parseRoute: #/git/commits/abc123 → single commit', () => {
  assert.deepStrictEqual(parseRoute('#/git/commits/abc123'), { page: 'git', view: 'commit', hash: 'abc123' });
});

test('parseRoute: #/unknown → file fallback', () => {
  assert.deepStrictEqual(parseRoute('#/unknown'), { page: 'file' });
});

// ── getShareableUrl ─────────────────────────────────

test('getShareableUrl: file only', () => {
  assert.strictEqual(getShareableUrl('http://localhost:8080/', 'src/App.java'), 'http://localhost:8080/?file=src%2FApp.java');
});

test('getShareableUrl: file and line', () => {
  assert.strictEqual(getShareableUrl('http://x/', 'a.java', 42), 'http://x/?file=a.java&line=42');
});

test('getShareableUrl: file, line, and symbol', () => {
  assert.strictEqual(getShareableUrl('http://x/', 'a.java', 10, 'main'), 'http://x/?file=a.java&line=10&symbol=main');
});

test('getShareableUrl: no params → bare base', () => {
  assert.strictEqual(getShareableUrl('http://x/'), 'http://x/');
});

