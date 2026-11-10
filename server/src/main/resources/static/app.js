/* State */
const state = { tabs: [], activeTab: null };

/* DOM refs */
const $tabBar      = document.getElementById('tab-bar');
const $breadcrumb  = document.getElementById('breadcrumb');
const $viewer      = document.getElementById('code-viewer');
const $fileTree    = document.getElementById('file-tree');
const $searchInput = document.getElementById('search-input');
const $searchPanel = document.getElementById('search-panel');
const $searchRes   = document.getElementById('search-results');
const $suggestDD   = document.getElementById('suggest-dropdown');

/* ── Tabs ─────────────────────────────────────────── */

function openTab(path) {
  const existing = state.tabs.find(t => t.path === path);
  if (existing) { activateTab(existing.id); return; }
  const name = path.split('/').pop();
  state.tabs.push({ id: path, path, name });
  activateTab(path);
}

function activateTab(id) {
  state.activeTab = id;
  renderTabs();
  loadFile(id);
}

function closeTab(id) {
  const idx = state.tabs.findIndex(t => t.id === id);
  if (idx === -1) return;
  state.tabs.splice(idx, 1);
  if (state.activeTab === id) {
    const next = state.tabs[idx] || state.tabs[idx - 1] || null;
    state.activeTab = next ? next.id : null;
  }
  renderTabs();
  if (state.activeTab) { loadFile(state.activeTab); }
  else { $viewer.innerHTML = emptyState(); $breadcrumb.innerHTML = ''; }
}

function renderTabs() {
  $tabBar.innerHTML = '';
  for (const tab of state.tabs) {
    const el = document.createElement('div');
    el.className = 'tab' + (tab.id === state.activeTab ? ' active' : '');
    el.setAttribute('role', 'tab');
    el.innerHTML = '<span>' + esc(tab.name) + '</span><span class="close-btn" title="Close">&times;</span>';
    el.addEventListener('click', e => {
      if (e.target.classList.contains('close-btn')) closeTab(tab.id);
      else activateTab(tab.id);
    });
    el.addEventListener('auxclick', e => { if (e.button === 1) closeTab(tab.id); });
    $tabBar.appendChild(el);
  }
}

/* ── File loading ─────────────────────────────────── */

async function loadFile(path) {
  try {
    const res = await fetch('/api/file?path=' + encodeURIComponent(path));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    renderCode(data.content, data.language);
    renderBreadcrumb(path);
  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

function renderCode(content, language) {
  const lines = content.split('\n');
  let html = '';
  for (let i = 0; i < lines.length; i++) {
    html += '<div class="code-line" data-line="' + (i + 1) + '">';
    html += '<span class="line-num">' + (i + 1) + '</span>';
    html += '<span class="line-content">' + esc(lines[i]) + '</span>';
    html += '</div>';
  }
  $viewer.innerHTML = html;

  if (language && hljs.getLanguage(language)) {
    $viewer.querySelectorAll('.line-content').forEach((el, i) => {
      try { el.innerHTML = hljs.highlight(lines[i], { language }).value; } catch (_) {}
    });
  }
  $viewer.scrollTop = 0;
}

/* ── Breadcrumb ───────────────────────────────────── */

function renderBreadcrumb(path) {
  const parts = path.split('/');
  let html = '';
  parts.forEach((part, i) => {
    if (i > 0) html += '<span class="sep">/</span>';
    const sub = parts.slice(0, i + 1).join('/');
    html += '<span data-path="' + esc(sub) + '">' + esc(part) + '</span>';
  });
  $breadcrumb.innerHTML = html;
  $breadcrumb.querySelectorAll('span[data-path]').forEach(el => {
    el.addEventListener('click', () => { if (el.dataset.path !== path) openTab(el.dataset.path); });
  });
}

/* ── File tree ────────────────────────────────────── */

async function loadDir(dirPath, parentEl) {
  try {
    const res = await fetch('/api/file/tree?path=' + encodeURIComponent(dirPath));
    const entries = await res.json();

    for (const e of entries) {
      const el = document.createElement('div');
      el.className = 'tree-item';
      el.style.paddingLeft = (12 + (parentEl ? parentEl.dataset.depth : 0) * 16) + 'px';

      if (e.type === 'directory') {
        el.innerHTML = '<span class="icon">' + dirIcon() + '</span><span>' + esc(e.name) + '</span>';
        el.dataset.depth = parentEl ? Number(parentEl.dataset.depth) + 1 : 1;
        el.addEventListener('click', () => {
          if (el.dataset.loaded) { el.nextElementSibling && (el.nextElementSibling.style.display = none); el.dataset.loaded = '0'; return; }
          const container = document.createElement('div');
          container.style.display = 'block';
          el.after(container);
          loadDir(e.path, el);
          el.dataset.loaded = '1';
        });
      } else {
        const ext = e.name.split('.').pop();
        el.innerHTML = '<span class="icon">' + fileIcon(ext) + '</span><span>' + esc(e.name) + '</span>';
        el.addEventListener('click', () => {
          $fileTree.querySelectorAll('.tree-item.active').forEach(a => a.classList.remove('active'));
          el.classList.add('active');
          openTab(e.path);
        });
      }

      if (parentEl && parentEl.nextElementSibling) parentEl.nextElementSibling.appendChild(el);
      else parentEl.appendChild(el);
    }
  } catch (err) { console.error('Dir load failed:', err); }
}

function loadFileTree() {
  const label = $fileTree.querySelector('.section-label');
  $fileTree.innerHTML = '';
  $fileTree.appendChild(label);

  const root = document.createElement('div');
  root.className = 'tree-item';
  root.dataset.depth = '0';
  root.innerHTML = '<span class="icon">' + dirIcon() + '</span><span>src</span>';
  root.addEventListener('click', () => { loadDir('', root); });
  $fileTree.appendChild(root);

  loadDir('', null);
}

function dirIcon() {
  return '<svg viewBox="0 0 16 16" width="16" height="16"><path d="M1 3h5l2 2h7v8a1 1 0 01-1 1H2a1 1 0 01-1-1V3z" fill="#e8a33d"/></svg>';
}

/* ── Search ───────────────────────────────────────── */

async function doSearch(query) {
  if (!query || query.length < 2) return;
  try {
    const res = await fetch('/api/search?q=' + encodeURIComponent(query));
    const results = await res.json();
    $searchRes.innerHTML = '';

    for (const r of results) {
      const el = document.createElement('div');
      el.className = 'result-item';
      el.innerHTML = '<div class="file-path">' + esc(r.filePath) + ':' + r.lineStart + '</div><div class="match-line">' + esc(r.name || r.qualifiedName || '') + '</div>';
      el.addEventListener('click', () => { openTab(r.filePath); closeSearch(); });
      $searchRes.appendChild(el);
    }

    if (results.length === 0) $searchRes.innerHTML = '<div class="empty-state"><span>No results</span></div>';
    $searchPanel.classList.add('open');
  } catch (err) { console.error('Search failed:', err); }
}

function closeSearch() { $searchPanel.classList.remove('open'); }

/* ── Keyboard shortcuts ───────────────────────────── */

document.addEventListener('keydown', e => {
  if (e.ctrlKey && e.shiftKey && e.key === 'F') { e.preventDefault(); $searchInput.focus(); $searchInput.select(); }
  if (e.ctrlKey && e.key === 'b') { e.preventDefault(); }
  if (e.key === 'Escape') { closeSearch(); $searchInput.blur(); }
  if (e.ctrlKey && e.key === 'w') { e.preventDefault(); if (state.activeTab) closeTab(state.activeTab); }
  if (e.ctrlKey && e.key === 'Tab') {
    e.preventDefault();
    if (state.tabs.length < 2) return;
    const idx = state.tabs.findIndex(t => t.id === state.activeTab);
    const dir = e.shiftKey ? -1 : 1;
    const next = state.tabs[(idx + dir + state.tabs.length) % state.tabs.length];
    activateTab(next.id);
  }
});

let searchTimer = null;
let suggestItems = [];   // flat list of {hit, el} for keyboard nav
let suggestActive = -1;  // index of highlighted item (-1 = none)

async function doSuggest(query) {
  if (!query || query.length < 2) { closeSuggest(); return; }
  try {
    const res = await fetch('/api/search/suggest?q=' + encodeURIComponent(query) + '&limit=10');
    const results = await res.json();
    renderSuggest(results);
  } catch (err) { console.error('Suggest failed:', err); closeSuggest(); }
}

function renderSuggest(results) {
  $suggestDD.innerHTML = '';
  suggestItems = [];
  suggestActive = -1;

  if (!results || results.length === 0) { closeSuggest(); return; }

  // Group by kind: CLASS, METHOD, FIELD, FILE
  const groups = {};
  for (const r of results) {
    const kind = r.kind || 'FILE';
    if (!groups[kind]) groups[kind] = [];
    groups[kind].push(r);
  }

  const order = ['CLASS', 'METHOD', 'FIELD', 'FILE'];
  for (const kind of order) {
    const items = groups[kind];
    if (!items || items.length === 0) continue;

    const header = document.createElement('div');
    header.className = 'suggest-group';
    header.textContent = kind.toLowerCase() + 's';
    $suggestDD.appendChild(header);

    for (const r of items) {
      const el = document.createElement('div');
      el.className = 'suggest-item';
      el.setAttribute('role', 'option');
      el.innerHTML = '<span class="suggest-kind ' + esc(kind) + '">' + esc(kind) + '</span>' +
        '<span class="suggest-name">' + esc(r.name || r.qualifiedName || '') + '</span>' +
        '<span class="suggest-path">' + esc(r.filePath || '') + '</span>';
      el.addEventListener('click', () => selectSuggest(r));
      $suggestDD.appendChild(el);
      suggestItems.push({ hit: r, el });
    }
  }

  $suggestDD.classList.add('open');
}

function selectSuggest(hit) {
  closeSuggest();
  $searchInput.value = '';
  openTab(hit.filePath);
}

function closeSuggest() { $suggestDD.classList.remove('open'); suggestItems = []; suggestActive = -1; }

function moveSuggest(dir) {
  if (suggestItems.length === 0) return;
  suggestActive = (suggestActive + dir + suggestItems.length) % suggestItems.length;
  suggestItems.forEach((item, i) => item.el.classList.toggle('active', i === suggestActive));
  // Scroll active item into view
  const activeEl = suggestItems[suggestActive].el;
  activeEl.scrollIntoView({ block: 'nearest' });
}

// Input handler: debounce both suggest (250ms) and full search (600ms)
$searchInput.addEventListener('input', () => {
  clearTimeout(searchTimer);
  const val = $searchInput.value;

  // Suggest: fast, for typeahead dropdown
  searchTimer = setTimeout(() => doSuggest(val), 250);

  // Full search: slower, opens results panel (only on Enter or after longer pause)
});

// Keydown on search input: handle arrow keys, Enter, Escape for dropdown nav
$searchInput.addEventListener('keydown', e => {
  if (e.key === 'ArrowDown') { e.preventDefault(); moveSuggest(1); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); moveSuggest(-1); }
  else if (e.key === 'Enter') {
    e.preventDefault();
    if (suggestActive >= 0 && suggestItems[suggestActive]) { selectSuggest(suggestItems[suggestActive].hit); }
    else { closeSuggest(); doSearch($searchInput.value); }
  }
  else if (e.key === 'Escape') { closeSuggest(); $searchInput.blur(); }
});

// Close suggest dropdown when clicking outside
document.addEventListener('click', e => {
  if (!e.target.closest('.search-box')) closeSuggest();
});

document.getElementById('search-close').addEventListener('click', () => { closeSearch(); closeSuggest(); });

/* ── Helpers ──────────────────────────────────────── */

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

function emptyState() {
  return '<div class="welcome">' +
    '<div class="welcome-logo">Jolt</div>' +
    '<p class="welcome-sub">Java code explorer &amp; agent output reviewer</p>' +
    '<div class="welcome-hints">' +
      '<div class="hint-item">' +
        '<span class="hint-icon">&#x2022;</span>' +
        '<span>Search code with <kbd>Ctrl+Shift+F</kbd></span>' +
      '</div>' +
      '<div class="hint-item">' +
        '<span class="hint-icon">&#x2022;</span>' +
        '<span>Click files in the tree to open tabs</span>' +
      '</div>' +
      '<div class="hint-item git-hint">' +
        '<span class="hint-icon">&#x2022;</span>' +
        '<span>This project is tracked with Git. View commit history, diffs &amp; contributor stats <a href="/git" class="git-link">&rarr;</a></span>' +
      '</div>' +
    '</div>' +
  '</div>';
}

function fileIcon(type) {
  const c = { java:'#e76f00', javascript:'#f7df1e', typescript:'#3178c6', python:'#3572a5', html:'#e34c26', css:'#264de4' };
  const color = c[type] || '#808080';
  return '<svg viewBox="0 0 16 16" width="16" height="16"><rect x="2" y="2" width="12" height="12" rx="3" fill="' + color + '"/></svg>';
}

/* ── Settings panel ─────────────────────────────── */

const $settingsPanel = document.getElementById('settings-panel');
const $settingsBtn   = document.getElementById('settings-btn');
const $settingsClose = document.getElementById('settings-close');
const $settingsSave  = document.getElementById('settings-save');
const $settingsMsg   = document.getElementById('settings-saved-msg');

function openSettings() { loadSettings(); $settingsPanel.classList.add('open'); }
function closeSettings() { $settingsPanel.classList.remove('open'); }

async function loadSettings() {
  try {
    const res = await fetch('/api/settings');
    const s = await res.json();

    document.getElementById('set-fuzzy-edits').value   = s['search.fuzzy.maxEdits'] ?? 2;
    document.getElementById('set-suggest-limit').value  = s['search.suggest.limit'] ?? 10;
    document.getElementById('set-diff-maxlines').value  = s['git.diff.maxLines'] ?? 1000;
    document.getElementById('set-diff-view').value      = s['git.diff.defaultView'] ?? 'unified';

    $settingsMsg.textContent = '';
  } catch (err) { console.error('Settings load failed:', err); }
}

async function saveSettings() {
  const payload = {
    'search.fuzzy.maxEdits': parseInt(document.getElementById('set-fuzzy-edits').value, 10) || 2,
    'search.suggest.limit':  parseInt(document.getElementById('set-suggest-limit').value, 10) || 10,
    'git.diff.maxLines':     parseInt(document.getElementById('set-diff-maxlines').value, 10) || 1000,
    'git.diff.defaultView':  document.getElementById('set-diff-view').value || 'unified'
  };

  try {
    const res = await fetch('/api/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    $settingsMsg.textContent = 'Saved ✓';
    setTimeout(() => { $settingsMsg.textContent = ''; }, 2000);
  } catch (err) { console.error('Settings save failed:', err); $settingsMsg.textContent = ''; }
}

$settingsBtn.addEventListener('click', openSettings);
$settingsClose.addEventListener('click', closeSettings);
$settingsSave.addEventListener('click', saveSettings);

/* ── Init ─────────────────────────────────────────── */

loadFileTree();
/* ════════════════════════════════════════════════
   GIT PAGES — hash-based router (Stages 3–6)
   ════════════════════════════════════════════ */

const $viewer = document.getElementById('code-viewer');

function initRouter() {
  window.addEventListener('hashchange', route);
  route();
}

function route() {
  const hash = location.hash || '';
  const parts = hash.replace(/^#\/?/, '').split('/');

  // Close any open panels
  closeSearch(); closeSuggest(); closeSettings();

  if (parts[0] === 'git') {
    // Hide file tree, show git content in main area
    document.querySelector('.sidebar').style.display = 'none';
    document.querySelector('.tab-bar').style.display = 'none';
    document.querySelector('.breadcrumb').style.display = 'none';

    if (parts.length === 1) { renderGitStats(); }
    else if (parts[1] === 'commits') { renderCommitList(parts.slice(2)); }
    else if (parts[1] === 'commits' && parts[2]) { renderCommitDetail(parts[2]); }
    else { renderGitStats(); }
  } else {
    // Normal mode — show file tree, hide git pages
    document.querySelector('.sidebar').style.display = '';
    document.querySelector('.tab-bar').style.display = '';
    document.querySelector('.breadcrumb').style.display = '';

    // If we have an active tab, show it; otherwise welcome
    if (state.activeTab) { loadFile(state.activeTab); }
    else { $viewer.innerHTML = emptyState(); $breadcrumb.innerHTML = ''; }
  }
}

/* ── 3a: Git Stats Dashboard ─────────────────── */

async function renderGitStats() {
  $viewer.innerHTML = '<div class="loading">Loading stats…</div>';

  try {
    const res = await fetch('/api/git/stats');
    const s = await res.json();

    let html = '<div class="git-page">';
    html += '<div class="git-header"><h1>Repository Stats</h1><a href="#/git/commits" class="git-nav-link">View Commits &rarr;</a></div>';

    // Stat cards
    html += '<div class="stats-grid">';
    html += statCard('Total Commits', s.totalCommits || 0);
    html += statCard('Contributors', s.totalContributors || 0);
    html += statCard('First Commit', fmtDate(s.firstCommitDate));
    html += statCard('Last Commit', fmtDate(s.lastCommitDate));
    html += statCard('Current Branch', s.currentBranch || '—');
    html += statCard('Longest Streak', (s.longestStreak || 0) + ' days');
    html += '</div>';

    // Top committers table
    if (s.topCommitters && s.topCommitters.length > 0) {
      html += '<div class="section-block"><h2>Top Committers</h2><table class="data-table"><thead><tr><th>#</th><th>Name</th><th>Email</th><th>Commits</th></tr></thead><tbody>';
      s.topCommitters.forEach((c, i) => {
        const initials = c.name.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
        html += '<tr><td>' + (i + 1) + '</td><td><span class="avatar">' + esc(initials) + '</span> ' + esc(c.name) + '</td><td class="muted">' + esc(c.email) + '</td><td>' + c.commits + '</td></tr>';
      });
      html += '</tbody></table></div>';
    }

    // Busiest day flair (Stage 6b)
    if (s.busiestDay) {
      html += '<div class="flair-row"><span class="flair-badge">🔥 Busiest day: <strong>' + esc(s.busiestDay) + '</strong> (' + (s.busiestDayCount || 0) + ' commits)</span>';
      if (s.currentStreak > 0) html += ' <span class="flair-badge">📅 Current streak: <strong>' + s.currentStreak + ' days</strong></span>';
      html += '</div>';
    }

    // Heatmap (Stage 6a)
    html += '<div class="section-block"><h2>Activity Heatmap</h2><div id="heatmap-container"></div></div>';

    html += '</div>';
    $viewer.innerHTML = html;

    // Render heatmap after DOM is ready
    renderHeatmap(new Date().getFullYear());

  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed to load stats: ' + esc(err.message) + '</span></div>';
  }
}

function statCard(label, value) {
  return '<div class="stat-card"><div class="stat-value">' + esc(String(value)) + '</div><div class="stat-label">' + esc(label) + '</div></div>';
}

/* ── 3b: Commit List with Pagination ─────────── */

async function renderCommitList(params) {
  const page = parseInt(params[0]) || 0;
  const branch = params[1] || '';

  $viewer.innerHTML = '<div class="loading">Loading commits…</div>';

  try {
    const qs = new URLSearchParams({ page: page, size: 25 });
    if (branch) qs.set('branch', branch);

    const res = await fetch('/api/git/commits?' + qs.toString());
    const data = await res.json();

    let html = '<div class="git-page">';
    html += '<div class="git-header"><h1>Commits</h1><a href="#/git" class="git-nav-link">&larr; Stats</a></div>';

    // Filters bar
    html += '<div class="filter-bar">';
    html += '<input type="text" id="filter-author" placeholder="Filter by author…" value="">';
    html += '<input type="date" id="filter-from" title="From date">';
    html += '<input type="date" id="filter-to" title="To date">';
    html += '<button id="filter-apply" class="btn-small">Apply</button>';
    html += '</div>';

    // Commit list
    html += '<div class="commit-list">';
    if (data.commits && data.commits.length > 0) {
      for (const c of data.commits) {
        const initials = c.author.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
        html += '<div class="commit-row" data-hash="' + esc(c.hash) + '">';
        html += '<span class="avatar">' + esc(initials) + '</span>';
        html += '<div class="commit-info">';
        html += '<div class="commit-msg"><a href="#/git/commits/' + esc(c.hash) + '">' + esc(c.message) + '</a></div>';
        html += '<div class="commit-meta"><span class="muted">' + esc(c.author) + '</span> · <span class="muted">' + fmtDate(c.date) + '</span> · <span class="hash-badge">' + esc(c.abbrevHash) + '</span> · ' + c.filesChanged + ' files</div>';
        html += '</div></div>';
      }
    } else {
      html += '<div class="empty-state"><span>No commits found</span></div>';
    }
    html += '</div>';

    // Pagination
    if (data.totalPages > 1) {
      html += '<div class="pagination">';
      for (let p = 0; p < data.totalPages; p++) {
        if (p === page) html += '<span class="page-active">' + (p + 1) + '</span>';
        else html += '<a href="#/git/commits/' + p + '" class="page-link">' + (p + 1) + '</a>';
      }
      html += '</div>';
    }

    html += '</div>';
    $viewer.innerHTML = html;

    // Wire filter buttons
    document.getElementById('filter-apply').addEventListener('click', () => {
      const author = document.getElementById('filter-author').value;
      const from = document.getElementById('filter-from').value;
      const to = document.getElementById('filter-to').value;

      const qs = new URLSearchParams({ page: 0, size: 25 });
      if (author) qs.set('author', author);
      if (from) qs.set('from', from + 'T00:00:00Z');
      if (to) qs.set('to', to + 'T23:59:59Z');

      location.hash = '#/git/commits/0';
      // Re-fetch with filters via a direct fetch and re-render
      fetch('/api/git/commits?' + qs.toString()).then(r => r.json()).then(d => {
        renderCommitListFiltered(d);
      });
    });

  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

function renderCommitListFiltered(data) {
  let html = '<div class="git-page">';
  html += '<div class="git-header"><h1>Commits (filtered)</h1><a href="#/git" class="git-nav-link">&larr; Stats</a></div>';
  html += '<div class="commit-list">';

  if (data.commits && data.commits.length > 0) {
    for (const c of data.commits) {
      const initials = c.author.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
      html += '<div class="commit-row" data-hash="' + esc(c.hash) + '">';
      html += '<span class="avatar">' + esc(initials) + '</span>';
      html += '<div class="commit-info">';
      html += '<div class="commit-msg"><a href="#/git/commits/' + esc(c.hash) + '">' + esc(c.message) + '</a></div>';
      html += '<div class="commit-meta"><span class="muted">' + esc(c.author) + '</span> · <span class="muted">' + fmtDate(c.date) + '</span> · <span class="hash-badge">' + esc(c.abbrevHash) + '</span> · ' + c.filesChanged + ' files</div>';
      html += '</div></div>';
    }
  } else {
    html += '<div class="empty-state"><span>No commits match filters</span></div>';
  }

  html += '</div></div>';
  $viewer.innerHTML = html;
}

/* ── 4a: Commit Detail with Diff ─────────────── */

async function renderCommitDetail(hash) {
  $viewer.innerHTML = '<div class="loading">Loading commit…</div>';

  try {
    const res = await fetch('/api/git/commit/enhanced?hash=' + encodeURIComponent(hash));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    let html = '<div class="git-page">';

    // Header
    html += '<div class="git-header"><h1>Commit</h1><a href="#/git/commits" class="git-nav-link">&larr; All Commits</a></div>';
    html += '<div class="commit-detail-header">';
    html += '<div class="cd-row"><span class="hash-badge full">' + esc(d.hash) + '</span></div>';
    html += '<div class="cd-row"><strong>' + esc(d.author) + '</strong> <span class="muted">&lt;' + esc(d.email) + '&gt;</span> · ' + fmtDate(d.date) + '</div>';
    html += '<div class="cd-msg">' + esc(d.message) + '</div>';
    if (d.parents && d.parents.length > 0) {
      html += '<div class="cd-row muted">Parent(s): ' + d.parents.map(p => '<span class="hash-badge">' + p.substring(0, 8) + '</span>').join(', ') + '</div>';
    }
    html += '</div>';

    // View toggle (Stage 5a)
    html += '<div class="diff-view-toggle">';
    html += '<button data-view="unified" class="view-btn active">Unified</button>';
    html += '<button data-view="split" class="view-btn">Split</button>';
    html += '<button data-view="raw" class="view-btn">Raw</button>';
    html += '<a href="/api/git/commit/patch?hash=' + esc(hash) + '" class="btn-small" download>⬇ .patch</a>';
    html += '</div>';

    // File list sidebar
    const fileStats = d.fileStats || [];
    html += '<div class="commit-detail-body">';
    html += '<div class="file-list-sidebar">';

    for (let i = 0; i < fileStats.length; i++) {
      const f = fileStats[i];
      const statusClass = f.status === 'A' ? 'st-add' : f.status === 'D' ? 'st-del' : 'st-mod';
      const statusLabel = f.status === 'A' ? '+' : f.status === 'D' ? '−' : '~';
      html += '<div class="file-list-item" data-idx="' + i + '" data-path="' + esc(f.path) + '">';
      html += '<span class="file-status ' + statusClass + '">' + statusLabel + '</span>';
      html += '<span class="file-name">' + esc(f.path.split('/').pop()) + '</span>';
      html += '<span class="file-stats">+' + f.linesAdded + ' −' + f.linesRemoved + '</span>';
      if (f.isBinary) html += '<span class="binary-badge">BIN</span>';
      html += '</div>';
    }

    html += '</div>';

    // Diff content area
    html += '<div class="diff-content" id="diff-content"><div class="loading">Select a file to view diff…</div></div>';

    html += '</div></div>';
    $viewer.innerHTML = html;

    // Store file stats for diff loading
    window._commitData = d;

    // Wire file list clicks
    document.querySelectorAll('.file-list-item').forEach(el => {
      el.addEventListener('click', () => loadFileDiff(hash, el.dataset.path, el.dataset.idx));
    });

    // Wire view toggle buttons
    document.querySelectorAll('.view-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.view-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        window._diffView = btn.dataset.view;
        // Re-render current file if any is selected
        const activeFile = document.querySelector('.file-list-item.active');
        if (activeFile) loadFileDiff(hash, activeFile.dataset.path, activeFile.dataset.idx);
      });
    });

  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

async function loadFileDiff(commitHash, filePath, idx) {
  const diffArea = document.getElementById('diff-content');
  if (!diffArea) return;

  // Highlight active file in sidebar
  document.querySelectorAll('.file-list-item').forEach(el => el.classList.remove('active'));
  const activeEl = document.querySelector('.file-list-item[data-idx="' + idx + '"]');
  if (activeEl) activeEl.classList.add('active');

  const view = window._diffView || 'unified';

  // Check if binary file (Stage 5c)
  const fileStats = (window._commitData && window._commitData.fileStats) || [];
  const fstat = fileStats.find(f => f.path === filePath);
  if (fstat && fstat.isBinary) {
    diffArea.innerHTML = '<div class="binary-diff"><span class="binary-badge">Binary files differ</span><br><a href="/api/git/commit/raw?hash=' + encodeURIComponent(commitHash) + '&path=' + encodeURIComponent(filePath) + '" target="_blank" class="git-nav-link">View full size &rarr;</a></div>';
    return;
  }

  diffArea.innerHTML = '<div class="loading">Loading diff…</div>';

  try {
    if (view === 'raw') {
      // Stage 5b: raw file content at commit
      const res = await fetch('/api/git/commit/raw?hash=' + encodeURIComponent(commitHash) + '&path=' + encodeURIComponent(filePath));
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const text = await res.text();
      renderRawView(diffArea, filePath, text);
    } else {
      // Unified or split: fetch unified diff, then render accordingly
      const res = await fetch('/api/git/file-diff?to=' + encodeURIComponent(commitHash) + '&path=' + encodeURIComponent(filePath));
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();

      // Stage 4b: large file guardrail
      const maxLines = 1000; // from settings default
      const diffLines = data.diff ? data.diff.split('\n') : [];

      if (view === 'split') {
        renderSplitDiff(diffArea, filePath, diffLines);
      } else {
        renderUnifiedDiff(diffArea, filePath, diffLines, maxLines);
      }
    }
  } catch (err) {
    diffArea.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

/* ── Diff Rendering ──────────────────────────── */

function renderUnifiedDiff(container, path, lines, maxLines) {
  let html = '<div class="diff-file-header">' + esc(path) + '</div>';
  html += '<div class="diff-body">';

  let shown = 0; let truncated = false;
  for (const line of lines) {
    if (shown >= maxLines) { truncated = true; break; }

    let cls = 'diff-ctx'; let prefix = ' ';
    if (line.startsWith('+')) { cls = 'diff-add'; prefix = '+'; }
    else if (line.startsWith('-')) { cls = 'diff-del'; prefix = '-'; }
    else if (line.startsWith('@@')) { cls = 'diff-hunk'; prefix = '@@'; }
    else if (line.startsWith('---') || line.startsWith('+++')) { cls = 'diff-meta'; prefix = line[0]; }

    const content = line.substring(1);
    html += '<div class="diff-line ' + cls + '"><span class="diff-prefix">' + esc(prefix) + '</span><span class="diff-text">' + esc(content) + '</span></div>';
    shown++;
  }

  if (truncated) {
    html += '<div class="diff-truncated">… ' + (lines.length - maxLines) + ' more lines … <a href="/api/git/commit/raw?hash=' + encodeURIComponent(window._commitData.hash) + '&path=' + encodeURIComponent(path) + '" target="_blank">View full file</a></div>';
  }

  html += '</div>';
  container.innerHTML = html;
}

function renderSplitDiff(container, path, lines) {
  // Parse unified diff into old/new line pairs for side-by-side view
  let oldLines = [], newLines = [];

  for (const line of lines) {
    if (line.startsWith('+') && !line.startsWith('+++')) newLines.push(line.substring(1));
    else if (line.startsWith('-') && !line.startsWith('---')) oldLines.push(line.substring(1));
    else if (!line.startsWith('@@') && !line.startsWith('---') && !line.startsWith('+++')) {
      // Context line — add to both sides
      oldLines.push(line.substring(1));
      newLines.push(line.substring(1));
    }
  }

  // Pad to equal length
  const maxLen = Math.max(oldLines.length, newLines.length);
  while (oldLines.length < maxLen) oldLines.push('');
  while (newLines.length < maxLen) newLines.push('');

  let html = '<div class="diff-file-header">' + esc(path) + '</div>';
  html += '<div class="split-diff">';

  for (let i = 0; i < maxLen; i++) {
    const o = oldLines[i], n = newLines[i];
    const oCls = o === n ? 'split-ctx' : 'split-del';
    const nCls = o === n ? 'split-ctx' : 'split-add';

    html += '<div class="split-row">';
    html += '<div class="split-old"><span class="split-ln">' + (i + 1) + '</span><span class="' + oCls + '">' + esc(o) + '</span></div>';
    html += '<div class="split-new"><span class="split-ln">' + (i + 1) + '</span><span class="' + nCls + '">' + esc(n) + '</span></div>';
    html += '</div>';
  }

  html += '</div>';
  container.innerHTML = html;
}

function renderRawView(container, path, text) {
  const lines = text.split('\n');
  let html = '<div class="diff-file-header">' + esc(path) + ' <span class="muted">(' + lines.length + ' lines)</span></div>';
  html += '<div class="raw-view">';

  for (let i = 0; i < lines.length; i++) {
    html += '<div class="raw-line"><span class="line-num">' + (i + 1) + '</span><span>' + esc(lines[i]) + '</span></div>';
  }

  html += '</div>';
  container.innerHTML = html;
}

/* ── 6a: Activity Heatmap ────────────────────── */

async function renderHeatmap(year) {
  const container = document.getElementById('heatmap-container');
  if (!container) return;

  try {
    const res = await fetch('/api/git/activity?year=' + year);
    const data = await res.json();
    const cells = data.cells || [];

    if (cells.length === 0) { container.innerHTML = '<div class="muted">No activity data</div>'; return; }

    // Find max for color scaling
    const maxCount = Math.max(...cells.map(c => c.count), 1);

    // Build week columns (52-53 weeks × 7 days)
    let html = '<div class="heatmap">';

    // Month labels
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    let monthLabels = '';
    let lastMonth = -1;
    for (const cell of cells) {
      const m = parseInt(cell.date.substring(5, 7)) - 1;
      if (m !== lastMonth) { monthLabels += '<span class="hm-month">' + months[m] + '</span>'; lastMonth = m; }
      else { monthLabels += '<span class="hm-month-sp"></span>'; }
    }

    // Grid: each column is a week (7 cells)
    let week = 0;
    for (let i = 0; i < cells.length; i += 7) {
      html += '<div class="hm-week">';
      for (let d = 0; d < 7 && (i + d) < cells.length; d++) {
        const cell = cells[i + d];
        const ratio = cell.count / maxCount;
        let colorClass = 'hm-0';
        if (cell.count > 0) {
          if (ratio <= 0.25) colorClass = 'hm-1';
          else if (ratio <= 0.5) colorClass = 'hm-2';
          else if (ratio <= 0.75) colorClass = 'hm-3';
          else colorClass = 'hm-4';
        }

        html += '<div class="hm-cell ' + colorClass + '" title="' + cell.count + ' commits on ' + cell.date + '" data-date="' + cell.date + '"></div>';
      }
      html += '</div>';
      week++;
    }

    html += '</div>';

    // Legend
    html += '<div class="hm-legend"><span>Less</span><span class="hm-cell hm-0"></span><span class="hm-cell hm-1"></span><span class="hm-cell hm-2"></span><span class="hm-cell hm-3"></span><span class="hm-cell hm-4"></span><span>More</span></div>';

    container.innerHTML = html;

    // Click a day → filter commit list to that date
    container.querySelectorAll('.hm-cell[data-date]').forEach(el => {
      el.addEventListener('click', () => {
        const date = el.dataset.date;
        location.hash = '#/git/commits/0';
        // Set filters and re-fetch
        setTimeout(() => {
          fetch('/api/git/commits?page=0&size=25&from=' + date + 'T00:00:00Z&to=' + date + 'T23:59:59Z')
            .then(r => r.json())
            .then(d => renderCommitListFiltered(d));
        }, 100);
      });
    });

  } catch (err) { console.error('Heatmap failed:', err); }
}

/* ── Helpers ─────────────────────────────────── */

function fmtDate(isoStr) {
  if (!isoStr) return '—';
  try {
    const d = new Date(isoStr);
    return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  } catch (_) { return isoStr; }
}

/* ── Init router ─────────────────────────────── */

initRouter();
