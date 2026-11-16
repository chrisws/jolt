/* State */
export const state = { tabs: [], activeTab: null };

/* Sentinel ID for the singleton GIT tab */
export const GIT_TAB_ID = '__git_tab__';

/* DOM refs */
const $tabBar  = document.getElementById('tab-bar');
const $viewer  = document.getElementById('code-viewer');
const $crumb   = document.getElementById('breadcrumb');

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

function emptyState() {
  return '<div class="welcome">' +
    '<div class="mascot" aria-hidden="true"><svg viewBox="0 0 120 170" width="80" height="113" xmlns="http://www.w3.org/2000/svg"><path d="M68 8 L34 74 L56 74 L44 162 L88 62 L63 62 Z" fill="#FFD700" stroke="#E6A800" stroke-width="2.5" stroke-linejoin="round"/></svg></div>' +
    '<div class="welcome-logo">Jolt</div>' +
    '<p class="welcome-sub">Java code explorer &amp; agent output reviewer</p>' +
    '<div class="welcome-hints">' +
      '<div class="hint-item"><span class="hint-icon">&#x2022;</span><span>Search code with <kbd>Ctrl+Shift+F</kbd></span></div>' +
      '<div class="hint-item"><span class="hint-icon">&#x2022;</span><span>Click files in the tree to open tabs</span></div>' +
      '<div class="hint-item git-hint"><span class="hint-icon">&#x2022;</span><span>This project is tracked with Git. View commit history, diffs &amp; contributor stats <a href="#/git" class="git-link">&rarr; Git</a></span></div>' +
    '</div></div>';
}

/** Open (or activate) the singleton GIT tab and set its view. */
export function openGitTab(view, opts = {}) {
  const { skipRender = false } = opts;
  const existing = state.tabs.find(t => t.id === GIT_TAB_ID);
  if (existing) {
    existing.gitView = view;
    if (skipRender) {
      state.activeTab = GIT_TAB_ID;
      renderTabs();
    } else {
      activateTab(GIT_TAB_ID);
    }
    return;
  }
  state.tabs.push({ id: GIT_TAB_ID, path: GIT_TAB_ID, name: 'Git', isGit: true, gitView: view });
  if (skipRender) {
    state.activeTab = GIT_TAB_ID;
    renderTabs();
  } else {
    activateTab(GIT_TAB_ID);
  }
}

/** Switch the active view of an already-open GIT tab. */
export function setGitTabView(view) {
  const tab = state.tabs.find(t => t.id === GIT_TAB_ID);
  if (!tab) { openGitTab(view); return; }
  tab.gitView = view;
  if (state.activeTab === GIT_TAB_ID) {
    renderGitView(view);
  } else {
    activateTab(GIT_TAB_ID);
  }
}

function renderGitView(view) {
  import('./git-pages.js').then(m => {
    if (view === 'changes') m.renderGitChanges();
    else if (view === 'commits') m.renderCommitList([]);
    else m.renderGitStats(); // 'stats' or default
  });
}

export function openTab(path) {
  const existing = state.tabs.find(t => t.path === path);
  if (existing) { activateTab(existing.id); return; }
  const name = path.split('/').pop();
  state.tabs.push({ id: path, path, name });
  activateTab(path);
}

export function activateTab(id) {
  state.activeTab = id;
  renderTabs();
  // Show/hide breadcrumb bar based on tab type
  const $crumbBar = document.querySelector('.breadcrumb-bar');
  const tab = state.tabs.find(t => t.id === id);
  if ($crumbBar) { $crumbBar.style.display = (tab && tab.isGit) ? 'none' : ''; }
  // Notify nav-history and other listeners (avoids circular import)
  document.dispatchEvent(new CustomEvent('jolt:tab-activated', { detail: { path: id } }));
  // Lazy-import to avoid circular deps at load time
  if (tab && tab.isDiff) {
    // Diff tabs render their own content via renderDirtyDiff (called by diff-panel.js)
    return;
  }
  if (tab && tab.isGit) {
    renderGitView(tab.gitView || 'stats');
    return;
  }
  import('./file-viewer.js').then(m => m.loadFile(id));
}

export function closeTab(id) {
  const idx = state.tabs.findIndex(t => t.id === id);
  if (idx === -1) return;
  state.tabs.splice(idx, 1);
  if (state.activeTab === id) {
    const next = state.tabs[idx] || state.tabs[idx - 1] || null;
    state.activeTab = next ? next.id : null;
  }
  renderTabs();
  if (state.activeTab) { import('./file-viewer.js').then(m => m.loadFile(state.activeTab)); }
  else { $viewer.innerHTML = emptyState(); $crumb.innerHTML = ''; }
}

export function closeAllTabs() {
  if (state.tabs.length > 1 && !confirm('Close all ' + state.tabs.length + ' tabs?')) return;
  state.tabs = [];
  state.activeTab = null;
  renderTabs();
  $viewer.innerHTML = emptyState();
  $crumb.innerHTML = '';
}

export function renderTabs() {
  $tabBar.innerHTML = '';
  for (const tab of state.tabs) {
    const isActive = tab.id === state.activeTab;
    const el = document.createElement('div');
    el.className = 'tab' + (isActive ? ' active' : '') + (tab.isDiff ? ' diff-tab' : '') + (tab.isGit ? ' git-tab' : '');
    el.setAttribute('role', 'tab');
    el.setAttribute('aria-selected', isActive ? 'true' : 'false');
    el.setAttribute('tabindex', isActive ? '0' : '-1');
    let icon = '';
    if (tab.isDiff) icon = '<span class="diff-tab-icon" aria-hidden="true">\u00B1</span>';
    else if (tab.isGit) icon = '<span class="git-tab-icon" aria-hidden="true">\u{1F4CC}</span>';
    el.innerHTML = icon + '<span>' + esc(tab.name) + '</span><span class="close-btn" title="Close">&times;</span>';
    el.addEventListener('click', e => {
      if (e.target.classList.contains('close-btn')) closeTab(tab.id);
      else activateTab(tab.id);
    });
    el.addEventListener('auxclick', e => { if (e.button === 1) closeTab(tab.id); });
    $tabBar.appendChild(el);
  }

  // Home button (visible only when tabs are open)
  if (state.tabs.length > 0) {
    const homeBtn = document.createElement('button');
    homeBtn.className = 'tab-home-btn';
    homeBtn.title = 'Close all tabs / Go home';
    homeBtn.textContent = '\u2302'; // ⌂
    homeBtn.addEventListener('click', () => closeAllTabs());
    $tabBar.appendChild(homeBtn);
  }

  // Arrow key navigation within tab bar (roving tabindex)
  $tabBar.addEventListener('keydown', e => {
    const tabs = Array.from($tabBar.querySelectorAll('.tab'));
    const idx = tabs.findIndex(t => t.getAttribute('tabindex') === '0');
    if (idx === -1) return;
    let next = -1;
    if (e.key === 'ArrowRight') next = (idx + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') next = (idx - 1 + tabs.length) % tabs.length;
    else return;
    e.preventDefault();
    tabs[idx].setAttribute('tabindex', '-1');
    tabs[next].setAttribute('tabindex', '0');
    tabs[next].focus();
  });
}

export function showEmptyState() { $viewer.innerHTML = emptyState(); $crumb.innerHTML = ''; }
