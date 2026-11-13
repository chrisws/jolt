import { state, showEmptyState } from './tabs.js';
import { loadFile } from './file-viewer.js';
import { closeSearch } from './search.js';
import { closeSettings } from './settings.js';
import { renderGitStats, renderCommitList, renderCommitDetail, renderGitChanges } from './git-pages.js';
import { parseRoute, getShareableUrl as buildShareableUrl } from './url-utils.js';

const $viewer = document.getElementById('code-viewer');

export function initRouter() {
  // Handle shareable link query params on initial load: ?file=...&line=...&symbol=...
  handleShareableLink();
  window.addEventListener('hashchange', route);
  route();
}

function handleShareableLink() {
  const params = new URLSearchParams(location.search);
  const file = params.get('file');
  const line = params.get('line');
  const symbol = params.get('symbol');

  if (!file) return;

  // Open the file in a tab
  import('./tabs.js').then(m => {
    m.openTab(file);
    // If line specified, scroll to it after file loads
    if (line) {
      setTimeout(() => {
        const target = document.querySelector('.code-line[data-line="' + line + '"]');
        if (target) target.scrollIntoView({ block: 'center' });
      }, 300);
    }
  });

  // Clean up query params so refresh doesn't re-trigger
  const cleanUrl = location.pathname + location.hash;
  history.replaceState(null, '', cleanUrl);
}

/** Generate a shareable URL for the current file/line/symbol. */
export function getShareableUrl(filePath, line, symbol) {
  const base = location.origin + location.pathname;
  return buildShareableUrl(base, filePath, line, symbol);
}

/** Copy the current file's shareable URL to clipboard. */
export async function copyShareableLink() {
  const { state } = await import('./tabs.js');
  if (!state.activeTab) return;

  const url = getShareableUrl(state.activeTab);
  try {
    await navigator.clipboard.writeText(url);
    showLinkCopied();
  } catch (err) {
    console.error('Clipboard failed:', err);
  }
}

function showLinkCopied() {
  let toast = document.getElementById('link-toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'link-toast';
    toast.className = 'link-toast';
    document.body.appendChild(toast);
  }
  toast.textContent = 'Link copied to clipboard';
  toast.classList.add('visible');
  setTimeout(() => toast.classList.remove('visible'), 2000);
}

function route() {
  const hash = location.hash || '';
  const parts = hash.replace(/^#\/?/, '').split('/');

  // Close any open panels
  closeSearch();
  closeSettings();

  if (parts[0] === 'git') {
    // Hide file tree, show git content in main area
    document.querySelector('.sidebar').style.display = 'none';
    document.querySelector('.tab-bar').style.display = 'none';
    document.querySelector('.breadcrumb').style.display = 'none';

    if (parts.length === 1) { renderGitStats(); }
    else if (parts[1] === 'changes') { renderGitChanges(); }
    else if (parts[1] === 'commits' && parts[2]) { renderCommitDetail(parts[2]); }
    else if (parts[1] === 'commits') { renderCommitList(parts.slice(2)); }
    else { renderGitStats(); }
  } else if (parts[0] === 'dashboard') {
    // Dashboard — hide sidebar, show stats in main area
    document.querySelector('.sidebar').style.display = 'none';
    document.querySelector('.tab-bar').style.display = 'none';
    document.querySelector('.breadcrumb').style.display = 'none';

    renderDashboard();
  } else {
    // Normal mode — show file tree, hide git pages
    document.querySelector('.sidebar').style.display = '';
    document.querySelector('.tab-bar').style.display = '';
    document.querySelector('.breadcrumb').style.display = '';

    if (state.activeTab) { loadFile(state.activeTab); }
    else { showEmptyState(); }
  }
}

async function renderDashboard() {
  const $main = $viewer;
  $main.innerHTML = '<div class="empty-state"><span>Loading dashboard…</span></div>';

  try {
    const res = await fetch('/api/dashboard');
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();

    let html = '<div class="dashboard-page">';
    html += '<h2>Dashboard</h2>';
    html += '<div class="dashboard-grid">';

    for (const [key, val] of Object.entries(data)) {
      const label = key.replace(/([A-Z])/g, ' $1').replace(/^./, c => c.toUpperCase());
      html += '<div class="dashboard-card"><div class="dashboard-value">' + val + '</div><div class="dashboard-label">' + label + '</div></div>';
    }

    html += '</div></div>';
    $main.innerHTML = html;
  } catch (err) {
    $main.innerHTML = '<div class="empty-state"><span>Failed to load dashboard: ' + err.message + '</span></div>';
  }
}
