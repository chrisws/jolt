/* ── Word-level diff utilities (LCS-based token diff) ── */

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

export function tokenize(str) {
  const tokens = [];
  const re = /(\w+|\s+|[^\w\s])/g;
  let match;
  while ((match = re.exec(str)) !== null) { tokens.push(match[0]); }
  return tokens;
}

export function computeLCS(a, b) {
  const m = a.length;
  const n = b.length;
  const dp = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (a[i - 1] === b[j - 1]) dp[i][j] = dp[i - 1][j - 1] + 1;
      else dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
    }
  }
  const result = [];
  let i = m;
  let j = n;
  while (i > 0 && j > 0) {
    if (a[i - 1] === b[j - 1]) { result.unshift(a[i - 1]); i--; j--; }
    else if (dp[i - 1][j] > dp[i][j - 1]) i--;
    else j--;
  }
  return result;
}

export function wordDiffHighlight(oldStr, newStr) {
  const oldTokens = tokenize(oldStr);
  const newTokens = tokenize(newStr);
  if (oldTokens.length > 200 || newTokens.length > 200) return esc(newStr);
  const lcs = computeLCS(oldTokens, newTokens);
  let result = '';
  let lcsIdx = 0;
  for (const tok of newTokens) {
    if (lcsIdx < lcs.length && lcs[lcsIdx] === tok) { result += esc(tok); lcsIdx++; }
    else { result += '<ins class="word-diff">' + esc(tok) + '</ins>'; }
  }
  return result;
}

export function renderInlineDiff(container, path, lines) {
  let html = '<div class="diff-file-header">' + esc(path) + '</div>';
  html += '<div class="diff-body inline-diff">';
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (line.startsWith('@@')) { html += '<div class="diff-line diff-hunk"><span class="diff-prefix">@@</span><span class="diff-text">' + esc(line.substring(2)) + '</span></div>'; i++; }
    else if (line.startsWith('---') || line.startsWith('+++')) { i++; }
    else if (line.startsWith('-')) {
      const oldB = []; const newB = [];
      while (i < lines.length && lines[i].startsWith('-')) { oldB.push(lines[i].substring(1)); i++; }
      while (i < lines.length && lines[i].startsWith('+')) { newB.push(lines[i].substring(1)); i++; }
      for (let j = 0; j < Math.max(oldB.length, newB.length); j++) {
        const o = oldB[j] !== undefined ? oldB[j] : null;
        const n = newB[j] !== undefined ? newB[j] : null;
        if (o !== null && n !== null) html += '<div class="diff-line diff-mod"><span class="diff-prefix">~</span><span class="diff-text">' + wordDiffHighlight(o, n) + '</span></div>';
        else if (o !== null) html += '<div class="diff-line diff-del"><span class="diff-prefix">-</span><span class="diff-text">' + esc(o) + '</span></div>';
        else html += '<div class="diff-line diff-add"><span class="diff-prefix">+</span><span class="diff-text">' + esc(n) + '</span></div>';
      }
    } else if (line.startsWith('+')) { html += '<div class="diff-line diff-add"><span class="diff-prefix">+</span><span class="diff-text">' + esc(line.substring(1)) + '</span></div>'; i++; }
    else { html += '<div class="diff-line diff-ctx"><span class="diff-prefix"> </span><span class="diff-text">' + esc(line.substring(1)) + '</span></div>'; i++; }
  }
  html += '</div>';
  container.innerHTML = html;
}