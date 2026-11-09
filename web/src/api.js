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