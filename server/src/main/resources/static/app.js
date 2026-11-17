import { initSearch } from './modules/search.js';
import { initSettings } from './modules/settings.js';
import { initKeybindings } from './modules/keybindings.js';
import { initRouter } from './modules/router.js';
import { restoreSession } from './modules/tabs.js';
import { loadFileTree } from './modules/file-tree.js';
import { initInspector } from './modules/inspector.js';
import { initUserData } from './modules/user-data.js';
import { initFileViewer } from './modules/file-viewer.js';
import { initQuickOpen, openQuickOpen } from './modules/quick-open.js';
import { copyShareableLink } from './modules/router.js';
import { initBranchSelector } from './modules/branch-selector.js';
import { initNavHistory } from './modules/nav-history.js';
import { initDivider } from './modules/divider.js';
import { initContextMenu } from './modules/context-menu.js';
import { initSidebar } from './modules/sidebar.js';
import { initDiffPanel } from './modules/diff-panel.js';

// ── Auth gate ──────────────────────────────────────────────
async function checkAuth() {
  // Auto-exchange token from URL query param (e.g. ?token=abc123)
  const params = new URLSearchParams(window.location.search);
  const urlToken = params.get('token');
  if (urlToken) {
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: urlToken })
      });
      if (res.ok) {
        console.log('[AUTH] Token exchanged from URL');
        // Clean the token from the URL so it's not in history/referrer
        history.replaceState({}, '', window.location.pathname + window.location.hash);
      } else {
        console.warn('[AUTH] Token exchange failed:', res.status);
      }
    } catch (err) { console.error('[AUTH] Token exchange error:', err.message); }
  }

  try {
    const res = await fetch('/api/settings');
    if (res.status === 401) {
      window.location.replace('/login.html');
      return false;
    }
  } catch { /* network error — proceed, will show natural errors */ }
  return true;
}

if (!(await checkAuth())) {
  throw new Error('Auth required — redirecting to login');
}

initSidebar();

// ── Parallel startup ──────────────────────────────────────

async function loadTheme() {
  try {
    const res = await fetch('/api/settings');
    const settings = await res.json();
    const theme = settings.theme || 'dark';
    document.documentElement.dataset.theme = theme;
  } catch {
    // One-time notification for settings load failure
    const toast = document.createElement('div');
    toast.className = 'jolt-toast visible';
    toast.id = 'jolt-toast';
    toast.textContent = 'Settings unavailable — using defaults';
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 5000);
  }
}

// Synchronous init (event wiring) — safe to run in sequence
initSearch();
initSettings();
initKeybindings();
initInspector();
initRouter();
initUserData();
initFileViewer();
initBranchSelector();
initQuickOpen();
initDivider();
initContextMenu();
initNavHistory();
initDiffPanel();

// Restore tabs from previous session (only if router didn't open one via URL hash)
import('./modules/tabs.js').then(m => {
  if (!m.state.activeTab) { restoreSession(); }
});

// ── Index progress indicator ──────────────────────────────
let _indexProgEl = null;

function showIndexProgress(current, total, percent) {
  if (!_indexProgEl) {
    _indexProgEl = document.createElement('div');
    _indexProgEl.id = 'jolt-index-progress';
    Object.assign(_indexProgEl.style, {
      position: 'fixed', bottom: '0', left: '0', right: '0', zIndex: '9998',
      background: 'rgba(30,30,30,0.92)', color: '#ccc', fontSize: '12px',
      padding: '4px 12px', display: 'flex', alignItems: 'center', gap: '10px'
    });
    const bar = document.createElement('div');
    bar.id = 'jolt-index-bar';
    Object.assign(bar.style, {
      flex: '1', height: '4px', background: '#333', borderRadius: '2px', overflow: 'hidden'
    });
    const fill = document.createElement('div');
    fill.id = 'jolt-index-fill';
    Object.assign(fill.style, { height: '100%', width: '0%', background: '#4a9eff', transition: 'width 0.3s' });
    bar.appendChild(fill);
    const label = document.createElement('span');
    label.id = 'jolt-index-label';
    _indexProgEl.appendChild(bar);
    _indexProgEl.appendChild(label);
    document.body.appendChild(_indexProgEl);
  }

  const fill = document.getElementById('jolt-index-fill');
  const label = document.getElementById('jolt-index-label');
  if (fill) fill.style.width = percent + '%';
  if (label) label.textContent = 'Indexing… ' + current + '/' + total + ' (' + percent + '%)';

  if (percent >= 100) {
    setTimeout(() => { if (_indexProgEl) { _indexProgEl.remove(); _indexProgEl = null; } }, 2500);
  }
}

// ── WebSocket with auto-reconnect ──────────────────────────
(function initWs() {
  let ws = null;
  let reconnectDelay = 1000; // start at 1s, max 30s

  // Debounce tree refreshes on rapid git operations (500 ms)
  let _treeTimer = null;
  function debouncedTreeRefresh() {
    if (_treeTimer) clearTimeout(_treeTimer);
    _treeTimer = setTimeout(() => {
      import('./modules/file-tree.js').then(m => m.markDirtyFiles());
    }, 500);
  }

  function connect() {
    ws = new WebSocket('ws://' + location.host + '/ws');

    ws.onopen = () => { reconnectDelay = 1000; }; // reset backoff on successful connect

    ws.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data);
        if (data.type === 'git_status_changed') {
          debouncedTreeRefresh();
          document.dispatchEvent(new CustomEvent('jolt:git-status-changed', { detail: data }));
        } else if (data.type === 'file_changed') {
          debouncedTreeRefresh();
        } else if (data.type === 'index_progress') {
          showIndexProgress(data.current, data.total, data.percent);
        } else if (data.type === 'ping') {
          // Respond to server heartbeat.
          ws.send(JSON.stringify({ type: 'pong' }));
        } else if (data.type === 'error') {
          console.warn('[WS] server error:', data.code, data.message);
        }
      } catch (err) { console.debug('[WS] parse error:', err.message); }
    };

    ws.onclose = () => {
      console.debug('[WS] connection closed, reconnecting in', reconnectDelay, 'ms');
      setTimeout(connect, reconnectDelay);
      reconnectDelay = Math.min(reconnectDelay * 2, 30000); // exponential backoff, cap 30s
    };

    ws.onerror = () => { /* onclose will fire after onerror */ };
  }

  connect();

  // Expose for modules that need to send WS messages (e.g. real-time search).
  window._joltWsSend = (obj) => { if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj)); };
})();

// Async init — run in parallel with theme loading
Promise.all([loadTheme(), loadFileTree()]);

// Expose for keybindings module
window._openQuickOpen = openQuickOpen;

// Copy Link button
document.getElementById('copy-link-btn').addEventListener('click', copyShareableLink);

// Bookmark button
document.getElementById('bookmark-btn').addEventListener('click', async () => {
  const { state } = await import('./modules/tabs.js');
  if (!state.activeTab) return;
  if (window._joltAddBookmark) window._joltAddBookmark(state.activeTab, 1, state.activeTab.split('/').pop());
});

// Theme toggle
const themeBtn = document.getElementById('theme-toggle');
themeBtn.addEventListener('click', async () => {
  const current = document.documentElement.dataset.theme || 'dark';
  const next = current === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  themeBtn.textContent = next === 'dark' ? '\u263D' : '\u2600';
  try { await fetch('/api/settings', { method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify({theme: next}) }); } catch {}
});

// ── Network offline / online banner ──────────────────────
(function initNetworkBanner() {
  let banner = null;

  function showBanner() {
    if (banner) return; // already visible
    banner = document.createElement('div');
    banner.id = 'jolt-network-banner';
    banner.textContent = '\u26A0 Connection lost \u2014 retrying\u2026';
    Object.assign(banner.style, {
      position: 'fixed', top: '0', left: '0', right: '0', zIndex: '9999',
      textAlign: 'center', padding: '6px 12px', fontSize: '13px', fontWeight: '600',
      color: '#fff', background: '#c0392b', letterSpacing: '0.3px'
    });
    document.body.appendChild(banner);
  }

  function hideBanner() {
    if (banner) { banner.remove(); banner = null; }
  }

  window.addEventListener('offline', showBanner);
  window.addEventListener('online', hideBanner);

  // If already offline on load, show immediately
  if (!navigator.onLine) showBanner();
})();
