/* State */
export const state = { tabs: [], activeTab: null };

/* DOM refs */
const $tabBar  = document.getElementById('tab-bar');
const $viewer  = document.getElementById('code-viewer');
const $crumb   = document.getElementById('breadcrumb');

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

function emptyState() {
  return '<div class="welcome">' +
    '<div class="welcome-logo">Jolt</div>' +
    '<p class="welcome-sub">Java code explorer &amp; agent output reviewer</p>' +
    '<div class="welcome-hints">' +
      '<div class="hint-item"><span class="hint-icon">&#x2022;</span><span>Search code with <kbd>Ctrl+Shift+F</kbd></span></div>' +
      '<div class="hint-item"><span class="hint-icon">&#x2022;</span><span>Click files in the tree to open tabs</span></div>' +
      '<div class="hint-item git-hint"><span class="hint-icon">&#x2022;</span><span>This project is tracked with Git. View commit history, diffs &amp; contributor stats <a href="/git" class="git-link">&rarr;</a></span></div>' +
    '</div></div>';
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
  // Notify nav-history and other listeners (avoids circular import)
  document.dispatchEvent(new CustomEvent('jolt:tab-activated', { detail: { path: id } }));
  // Lazy-import to avoid circular deps at load time
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
  state.tabs = [];
  state.activeTab = null;
  renderTabs();
  $viewer.innerHTML = emptyState();
  $crumb.innerHTML = '';
}

export function renderTabs() {
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

  // Home button (visible only when tabs are open)
  if (state.tabs.length > 0) {
    const homeBtn = document.createElement('button');
    homeBtn.className = 'tab-home-btn';
    homeBtn.title = 'Close all tabs / Go home';
    homeBtn.textContent = '\u2302'; // ⌂
    homeBtn.addEventListener('click', () => closeAllTabs());
    $tabBar.appendChild(homeBtn);
  }
}

export function showEmptyState() { $viewer.innerHTML = emptyState(); $crumb.innerHTML = ''; }
