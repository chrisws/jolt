const BASE = ''; // empty → uses Vite proxy in dev, same-origin in prod

export async function getStatus() {
  const res = await fetch(`${BASE}/api/status`);
  return res.json();
}

export async function search(query, kind = null, limit = 50) {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  if (kind) params.set('kind', kind);
  const res = await fetch(`${BASE}/api/search?${params}`);
  return res.json();
}

export async function searchSemantic(query, limit = 20) {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  const res = await fetch(`${BASE}/api/search/semantic?${params}`);
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Semantic search failed (${res.status})`);
  }
  return res.json();
}

export async function getFile(path, startLine = null, endLine = null) {
  const params = new URLSearchParams({ path });
  if (startLine != null) params.set('start', String(startLine));
  if (endLine != null) params.set('end', String(endLine));
  const res = await fetch(`${BASE}/api/file?${params}`);
  return res.json();
}

export async function getDirectoryTree(path = '') {
  const params = new URLSearchParams({ path });
  const res = await fetch(`${BASE}/api/file/tree?${params}`);
  return res.json();
}

export async function getDefinition(symbol) {
  const res = await fetch(`${BASE}/api/definition?symbol=${encodeURIComponent(symbol)}`);
  return res.json();
}

export async function getHierarchy(cls) {
  const res = await fetch(`${BASE}/api/hierarchy?class=${encodeURIComponent(cls)}`);
  return res.json();
}

export async function getReferences(symbol) {
  const res = await fetch(`${BASE}/api/references?symbol=${encodeURIComponent(symbol)}`);
  return res.json();
}

export async function getBlame(path, start = 1, end = null) {
  const params = new URLSearchParams({ path, start: String(start) });
  if (end != null) params.set('end', String(end));
  const res = await fetch(`${BASE}/api/blame?${params}`);
  return res.json();
}

export async function getDashboard() {
  const res = await fetch(`${BASE}/api/dashboard`);
  return res.json();
}

// ─── Phase 2: Navigation ────────────────────────────────────────

export async function getCallGraph(method, depth = 3) {
  const params = new URLSearchParams({ method, depth: String(depth) });
  const res = await fetch(`${BASE}/api/call-graph?${params}`);
  return res.json();
}

export async function getCallGraphCallers(method) {
  const params = new URLSearchParams({ method });
  const res = await fetch(`${BASE}/api/call-graph/callers?${params}`);
  return res.json();
}

export async function parseJsp(path) {
  const params = new URLSearchParams({ path });
  const res = await fetch(`${BASE}/api/jsp/parse?${params}`);
  return res.json();
}

export async function getSpringBeans(path) {
  const params = new URLSearchParams({ path });
  const res = await fetch(`${BASE}/api/spring/beans?${params}`);
  return res.json();
}

// ─── Phase 3: Git Review ────────────────────────────────────────

export async function getGitLog(count = 30, author = null, prefix = null) {
  const params = new URLSearchParams({ count: String(count) });
  if (author) params.set('author', author);
  if (prefix) params.set('prefix', prefix);
  const res = await fetch(`${BASE}/api/git/log?${params}`);
  return res.json();
}

export async function getGitDiff(from, to) {
  const params = new URLSearchParams({ to });
  if (from) params.set('from', from);
  const res = await fetch(`${BASE}/api/git/diff?${params}`);
  return res.json();
}

export async function getGitFileDiff(from, to, path) {
  const params = new URLSearchParams({ to, path });
  if (from) params.set('from', from);
  const res = await fetch(`${BASE}/api/git/file-diff?${params}`);
  return res.json();
}

export async function getGitBranches() {
  const res = await fetch(`${BASE}/api/git/branches`);
  return res.json();
}

export async function getCurrentBranch() {
  const res = await fetch(`${BASE}/api/git/branch`);
  return res.json();
}

export async function getCommitDetail(hash) {
  const params = new URLSearchParams({ hash });
  const res = await fetch(`${BASE}/api/git/commit?${params}`);
  return res.json();
}

// ─── Phase 4: J2EE ──────────────────────────────────────────────

export async function getWebXml() {
  const res = await fetch(`${BASE}/api/j2ee/webxml`);
  return res.json();
}

export async function getPersistenceXml() {
  const res = await fetch(`${BASE}/api/j2ee/persistence`);
  return res.json();
}

export async function getAnnotations() {
  const res = await fetch(`${BASE}/api/j2ee/annotations`);
  return res.json();
}

export async function getWiringGraph() {
  const res = await fetch(`${BASE}/api/j2ee/wiring-graph`);
  return res.json();
}

// ─── Phase 5: Bookmarks & History ───────────────────────────────

export async function getBookmarks() {
  const res = await fetch(`${BASE}/api/bookmarks`);
  return res.json();
}

export async function addBookmark(path, line, label = '') {
  const res = await fetch(`${BASE}/api/bookmarks`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path, line, label })
  });
  return res.json();
}

export async function removeBookmark(path, line) {
  const params = new URLSearchParams({ path, line: String(line) });
  const res = await fetch(`${BASE}/api/bookmarks?${params}`, { method: 'DELETE' });
  return res.json();
}

export async function getRecentFiles() {
  const res = await fetch(`${BASE}/api/recent`);
  return res.json();
}

export async function getSearchHistory() {
  const res = await fetch(`${BASE}/api/search/history`);
  return res.json();
}
