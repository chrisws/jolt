import { initSearch } from './modules/search.js';
import { initSettings } from './modules/settings.js';
import { initKeybindings } from './modules/keybindings.js';
import { initRouter } from './modules/router.js';
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
