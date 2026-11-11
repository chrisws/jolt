/* ── Pure URL/route utilities (no DOM dependencies) ── */

export function buildSearchUrl(query, opts = {}) {
  const { semantic = false, caseSensitive: cs = false, wholeWord: ww = false } = opts;
  let url;
  if (semantic) {
    url = '/api/search/semantic?q=' + encodeURIComponent(query);
  } else {
    url = '/api/search?q=' + encodeURIComponent(query);
  }
  if (cs) url += '&cs=1';
  if (ww) url += '&ww=1';
  return url;
}

export function parseRoute(hash) {
  const parts = (hash || '').replace(/^#\/?/, '').split('/');
  const page = parts[0] || 'file';

  if (page === 'git') {
    if (parts.length === 1) return { page: 'git', view: 'stats' };
    if (parts[1] === 'commits' && parts[2]) return { page: 'git', view: 'commit', hash: parts[2] };
    if (parts[1] === 'commits') return { page: 'git', view: 'commits', branch: parts.slice(2) };
    return { page: 'git', view: 'stats' };
  }
  if (page === 'dashboard') return { page: 'dashboard' };
  return { page: 'file' };
}

export function getShareableUrl(base, filePath, line, symbol) {
  const params = new URLSearchParams();
  if (filePath) params.set('file', filePath);
  if (line) params.set('line', String(line));
  if (symbol) params.set('symbol', symbol);
  const qs = params.toString();
  return base + (qs ? '?' + qs : '');
}