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

// Theme toggle
const themeBtn = document.getElementById('theme-toggle');
themeBtn.addEventListener('click', async () => {
  const current = document.documentElement.dataset.theme || 'dark';
  const next = current === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  themeBtn.textContent = next === 'dark' ? '\u263D' : '\u2600';
  try { await fetch('/api/settings', { method: 'PUT', headers: {'Content-Type':'application/json'}, body: JSON.stringify({theme: next}) }); } catch {}
});
