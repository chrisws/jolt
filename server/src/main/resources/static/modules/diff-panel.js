/* ── Diff Panel: manages diff view (tab or popup mode) ── */

import { authFetch } from './url-utils.js';

const $diffBtn     = document.getElementById('diff-btn');
const $diffPanel   = document.getElementById('diff-panel');
const $diffBody    = document.getElementById('diff-panel-body');
const $diffClose   = document.getElementById('diff-panel-close');

let currentDiffPath = null;

/** Read the user's preferred diff display mode from settings. */
async function getDiffMode() {
  try {
    const res = await authFetch('/api/settings');
    const s = await res.json();
    return s['git.diff.viewMode'] || 'tab';
  } catch { return 'tab'; }
}

/** Check if a given file path has uncommitted changes. */
async function isFileDirty(path) {
  try {
    const res = await authFetch('/api/git/status');
    if (!res.ok) return false;
    const entries = await res.json();
    return entries.some(e => e.path === path);
  } catch { return false; }
}

/** Show or hide the Diff button based on whether the active file is dirty. */
export async function updateDiffButton(activePath) {
  if (!activePath) { $diffBtn.style.display = 'none'; return; }
  const dirty = await isFileDirty(activePath);
  $diffBtn.style.display = dirty ? '' : 'none';
}

/** Open the diff for a given file path, respecting the user's mode preference. */
export async function openDiff(path) {
  const mode = await getDiffMode();

  if (mode === 'popup') {
    openPopup(path);
  } else {
    openDiffTab(path);
  }

  currentDiffPath = path;
}

/* ── Popup mode ─────────────────────────────────── */

function openPopup(path) {
  $diffPanel.removeAttribute('inert');
  $diffPanel.classList.add('open');
  $diffPanel.setAttribute('aria-hidden', 'false');

  import('./git-pages.js').then(m => m.renderDirtyDiff(path, $diffBody, { isPopup: true }));
}

export function closePopup() {
  $diffPanel.classList.remove('open');
  $diffPanel.setAttribute('inert', '');
  $diffPanel.setAttribute('aria-hidden', 'true');
  $diffBody.innerHTML = '';
}

/* ── Tab mode ─────────────────────────────────── */

async function openDiffTab(path) {
  const { state, activateTab } = await import('./tabs.js');

  // Find existing diff tab (only one allowed)
  const diffTab = state.tabs.find(t => t.isDiff);

  if (diffTab) {
    // Update content if different file, or just activate
    diffTab.path = path;
    diffTab.name = path.split('/').pop() + ' (diff)';
    activateTab(diffTab.id);
    // Re-render with new path
    import('./git-pages.js').then(m => m.renderDirtyDiff(path, document.getElementById('code-viewer')));
  } else {
    // Create new diff tab
    const name = path.split('/').pop() + ' (diff)';
    const id = '__diff__';
    state.tabs.push({ id, path, name, isDiff: true });
    activateTab(id);
    import('./git-pages.js').then(m => m.renderDirtyDiff(path, document.getElementById('code-viewer')));
  }

  // Hide Blame/Link/Bookmark when diff tab is active
  setBreadcrumbButtonsHidden(true);
}

function setBreadcrumbButtonsHidden(hidden) {
  const btns = ['blame-toggle', 'copy-link-btn', 'bookmark-btn', 'lint-btn'];
  btns.forEach(id => {
    const el = document.getElementById(id);
    if (el) el.style.display = hidden ? 'none' : '';
  });
}

/** Restore breadcrumb buttons when a non-diff tab is active. */
export async function onTabActivated(path) {
  const { state } = await import('./tabs.js');
  const activeTab = state.tabs.find(t => t.id === state.activeTab);
  const isDiffActive = activeTab && activeTab.isDiff;

  setBreadcrumbButtonsHidden(isDiffActive);

  // Update diff button visibility for source tabs
  if (!isDiffActive) {
    updateDiffButton(state.activeTab);
  } else {
    $diffBtn.style.display = 'none';
  }
}

/* ── Init ─────────────────────────────────── */

export function initDiffPanel() {
  // Diff button click
  $diffBtn.addEventListener('click', async () => {
    const { state } = await import('./tabs.js');
    if (state.activeTab) openDiff(state.activeTab);
  });

  // Popup close button
  $diffClose.addEventListener('click', closePopup);

  // Esc key closes popup
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && $diffPanel.classList.contains('open')) {
      closePopup();
    }
  });

  // Listen for tab activation to update button visibility
  document.addEventListener('jolt:tab-activated', e => {
    onTabActivated(e.detail.path);
  });

  // Initial state: hide diff button until a file is open
  $diffBtn.style.display = 'none';
}

