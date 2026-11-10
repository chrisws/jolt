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
$searchInput.addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => doSearch($searchInput.value), 300); });
$searchInput.addEventListener('keydown', e => { if (e.key === 'Enter') doSearch($searchInput.value); });
document.getElementById('search-close').addEventListener('click', closeSearch);

/* ── Helpers ──────────────────────────────────────── */

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

function emptyState() {
  return '<div class="empty-state"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M16 18l6-6-6-6M8 6l-6 6 6 6"/></svg><span>Select a file to view</span></div>';
}

function fileIcon(type) {
  const c = { java:'#e76f00', javascript:'#f7df1e', typescript:'#3178c6', python:'#3572a5', html:'#e34c26', css:'#264de4' };
  const color = c[type] || '#808080';
  return '<svg viewBox="0 0 16 16" width="16" height="16"><rect x="2" y="2" width="12" height="12" rx="3" fill="' + color + '"/></svg>';
}

/* ── Init ─────────────────────────────────────────── */

loadFileTree();