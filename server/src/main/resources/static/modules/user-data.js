import { authFetch } from './url-utils.js';

const $bookmarks = document.getElementById('bookmark-list');
const $recent    = document.getElementById('recent-list');
const $feedbackModal = document.getElementById('feedback-modal');

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

// ── Toast notification helper ──────────────────────────────
function showToast(msg, duration = 4000, action) {
  let toast = document.getElementById('jolt-toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'jolt-toast';
    toast.className = 'jolt-toast';
    document.body.appendChild(toast);
  }
  toast.innerHTML = '<span>' + esc(msg) + '</span>';
  if (action) {
    const btn = document.createElement('button');
    btn.className = 'toast-action';
    btn.textContent = action.label;
    btn.addEventListener('click', () => { action.fn(); hideToast(); });
    toast.appendChild(btn);
  }
  toast.classList.add('visible');
  clearTimeout(toast._timer);
  toast._timer = setTimeout(hideToast, duration);
}

function hideToast() {
  const toast = document.getElementById('jolt-toast');
  if (toast) toast.classList.remove('visible');
}

// ── Bookmarks ──────────────────────────────────────────────

async function loadBookmarks() {
  try {
    const res = await fetch('/api/bookmarks');
    const items = await res.json();
    $bookmarks.innerHTML = '';

    if (!items || items.length === 0) {
      $bookmarks.innerHTML = '<div class="empty-hint">No bookmarks</div>';
      return;
    }

    for (const b of items) {
      const el = document.createElement('div');
      el.className = 'bookmark-item';
      el.innerHTML = '<span class="bm-label">' + esc(b.label || b.path.split('/').pop()) + '</span>' +
        '<span class="bm-path">' + esc(b.path) + ':' + b.line + '</span>' +
        '<button class="bm-delete" title="Remove bookmark">&times;</button>';

      el.querySelector('.bm-delete').addEventListener('click', (e) => {
        e.stopPropagation();
        deleteBookmark(b);
      });

      el.addEventListener('click', () => {
        import('./tabs.js').then(m => m.openTab(b.path));
      });

      $bookmarks.appendChild(el);
    }
  } catch (err) { showToast('Failed to load bookmarks'); }
}

async function addBookmark(path, line, label) {
  try {
    await authFetch('/api/bookmarks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path, line, label: label || path.split('/').pop() })
    });
    loadBookmarks();
  } catch (err) { showToast('Failed to add bookmark'); }
}

async function deleteBookmark(bm) {
  try {
    await authFetch('/api/bookmarks', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: bm.path, line: bm.line })
    });

    // Show undo toast for 5 seconds
    showToast('Bookmark removed', 5000, {
      label: 'Undo',
      fn: () => { addBookmark(bm.path, bm.line, bm.label); }
    });

    loadBookmarks();
  } catch (err) { showToast('Failed to delete bookmark'); }
}

// ── Recent Files ───────────────────────────────────────────

async function loadRecent() {
  try {
    const res = await fetch('/api/recent');
    const items = await res.json();
    $recent.innerHTML = '';

    if (!items || items.length === 0) {
      $recent.innerHTML = '<div class="empty-hint">No recent files</div>';
      return;
    }

    for (const r of items) {
      const el = document.createElement('div');
      el.className = 'recent-item';
      const name = r.path ? r.path.split('/').pop() : r;
      el.innerHTML = '<span class="rc-name">' + esc(name) + '</span>' +
        '<span class="rc-path">' + esc(r.path || r) + '</span>';

      el.addEventListener('click', () => {
        import('./tabs.js').then(m => m.openTab(r.path || r));
      });

      $recent.appendChild(el);
    }
  } catch (err) { showToast('Failed to load recent files'); }
}

// ── Search History ─────────────────────────────────────────

async function loadSearchHistory() {
  try {
    const res = await fetch('/api/search/history');
    const items = await res.json();

    const $histList = document.getElementById('search-history-list');
    if (!$histList) return;

    $histList.innerHTML = '';

    if (!items || items.length === 0) {
      $histList.innerHTML = '<div class="empty-hint">No search history</div>';
      return;
    }

    for (const q of items) {
      const el = document.createElement('div');
      el.className = 'hist-item';
      el.textContent = q;
      el.addEventListener('click', () => {
        const $input = document.getElementById('search-input');
        $input.value = q;
        $input.dispatchEvent(new Event('input'));
        closeSearchHistory();
      });
      $histList.appendChild(el);
    }

    document.getElementById('search-history').classList.add('open');
  } catch (err) { showToast('Failed to load search history'); }
}

function closeSearchHistory() {
  const $hist = document.getElementById('search-history');
  if ($hist) $hist.classList.remove('open');
}

// ── Feedback Modal ─────────────────────────────────────────

function openFeedback(filePath, line) {
  const $pageField = document.getElementById('fb-page');
  const $fileField = document.getElementById('fb-file');

  if (filePath) {
    // Pre-fill from explicit params (e.g. clicking a code line)
    if ($pageField) $pageField.value = filePath;
    if ($fileField) $fileField.value = filePath + (line ? ':' + line : '');
  } else {
    // Use active tab path if available, else hash, else '/'
    import('./tabs.js').then(m => {
      if ($pageField) $pageField.value = m.state.activeTab || location.hash || '/';
    });
  }

  $feedbackModal.classList.add('open');

  const $msgField = document.getElementById('fb-message');
  if ($msgField) $msgField.focus();
}

/** Open feedback pre-filled for a specific code line. */
export function reportCodeIssue(filePath, line) {
  openFeedback(filePath, line);
}

function closeFeedback() {
  $feedbackModal.classList.remove('open');

  const $fileField = document.getElementById('fb-file');
  const $msgField = document.getElementById('fb-message');
  if ($fileField) $fileField.value = '';
  if ($msgField) $msgField.value = '';
}

async function submitFeedback(e) {
  e.preventDefault();

  const page    = document.getElementById('fb-page').value;
  const file    = document.getElementById('fb-file').value;
  const message = document.getElementById('fb-message').value;

  try {
    await authFetch('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ page, file, message })
    });

    closeFeedback();

    const $status = document.getElementById('fb-status');
    if ($status) {
      $status.textContent = 'Thanks! Feedback submitted.';
      setTimeout(() => { if ($status) $status.textContent = ''; }, 3000);
    }
  } catch (err) { showToast('Failed to submit feedback'); }
}

// ── Git Changes Section ────────────────────────────────────

const $gitChanges = document.getElementById('git-changes-list');

async function loadGitChanges() {
  if (!$gitChanges) return;

  // Ensure toggle exists
  let $toggle = document.getElementById('git-untracked-toggle');
  if (!$toggle) {
    $toggle = document.createElement('label');
    $toggle.id = 'git-untracked-toggle';
    $toggle.className = 'gc-toggle';
    $toggle.innerHTML = '<input type="checkbox" id="git-untracked-cb"> Show untracked';
    $gitChanges.parentElement.insertBefore($toggle, $gitChanges);
    document.getElementById('git-untracked-cb').addEventListener('change', loadGitChanges);
  }

  const showUntracked = document.getElementById('git-untracked-cb').checked;

  try {
    const qs = showUntracked ? '?untracked=true' : '';
    const res = await fetch('/api/git/status' + qs);
    if (!res.ok) return;
    const entries = await res.json();

    $gitChanges.innerHTML = '';

    if (!entries || entries.length === 0) {
      $gitChanges.innerHTML = '<div class="empty-hint">No changes</div>';
      return;
    }

    for (const entry of entries) {
      const p = entry.path;
      const type = entry.type;
      const el = document.createElement('div');
      el.className = 'git-change-item' + (type === 'untracked' ? ' gc-untracked' : '');
      const name = p.split('/').pop();

      let html = '<span class="gc-dot gc-dot-' + type + '"></span><span class="gc-name">' + esc(name) + '</span>';
      if (type === 'untracked') {
        html += '<button class="gc-add-btn" title="Stage file">+</button>';
      }
      el.innerHTML = html;
      el.title = p;

      el.addEventListener('click', (ev) => {
        if (ev.target.classList.contains('gc-add-btn')) return;
        showDirtyDiff(p);
      });

      if (type === 'untracked') {
        el.querySelector('.gc-add-btn').addEventListener('click', async (ev) => {
          ev.stopPropagation();
          const r = await authFetch('/api/git/add?path=' + encodeURIComponent(p), { method: 'POST' });
          if (r.ok) loadGitChanges();
        });
      }

      $gitChanges.appendChild(el);
    }
  } catch (_) { /* not a git repo */ }
}

async function showDirtyDiff(path) {
  const $viewer = document.getElementById('code-viewer');
  $viewer.innerHTML = '<div class="loading">Loading diff…</div>';

  try {
    const res = await fetch('/api/git/dirty-diff?path=' + encodeURIComponent(path));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const diffText = await res.text();

    let html = '<div class="git-page">';
    html += '<div class="git-header"><h1>Working Tree Diff</h1><a href="#" class="git-nav-link" id="dirty-diff-back">&larr; Back</a></div>';
    html += '<div class="diff-file-header">' + esc(path) + '</div>';
    html += '<div class="diff-body">';

    const lines = diffText.split('\n');
    for (const line of lines) {
      let cls = 'diff-ctx'; let prefix = ' ';
      if (line.startsWith('+') && !line.startsWith('+++')) { cls = 'diff-add'; prefix = '+'; }
      else if (line.startsWith('-') && !line.startsWith('---')) { cls = 'diff-del'; prefix = '-'; }
      else if (line.startsWith('@@')) { cls = 'diff-hunk'; prefix = '@@'; }
      else if (line.startsWith('---') || line.startsWith('+++')) { cls = 'diff-meta'; prefix = line[0]; }

      const content = line.substring(1);
      html += '<div class="diff-line ' + cls + '"><span class="diff-prefix">' + esc(prefix) + '</span><span class="diff-text">' + esc(content) + '</span></div>';
    }

    html += '</div></div>';
    $viewer.innerHTML = html;

    document.getElementById('dirty-diff-back').addEventListener('click', (e) => {
      e.preventDefault();
      location.hash = '';
    });

  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

// ── Init ───────────────────────────────────────────────────

export function initUserData() {
  // Bookmarks + Recent + Git Changes in sidebar (load on start)
  loadBookmarks();
  loadRecent();
  loadGitChanges();

  // Search history toggle button in search panel header
  const $histBtn = document.getElementById('search-history-btn');
  if ($histBtn) {
    $histBtn.addEventListener('click', () => {
      const $hist = document.getElementById('search-history');
      if ($hist.classList.contains('open')) { closeSearchHistory(); }
      else { loadSearchHistory(); }
    });

    // Close history on outside click
    document.addEventListener('click', (e) => {
      if (!e.target.closest('#search-history') && !e.target.closest('#search-history-btn')) {
        closeSearchHistory();
      }
    });
  }

  // Feedback button in settings panel
  const $fbBtn = document.getElementById('feedback-btn');
  if ($fbBtn) $fbBtn.addEventListener('click', () => openFeedback());

  // Feedback modal close + submit
  const $fbClose = document.getElementById('feedback-close');
  if ($fbClose) $fbClose.addEventListener('click', closeFeedback);

  const $fbForm = document.getElementById('feedback-form');
  if ($fbForm) $fbForm.addEventListener('submit', submitFeedback);

  // Close feedback on backdrop click
  if ($feedbackModal) {
    $feedbackModal.addEventListener('click', (e) => {
      if (e.target === $feedbackModal) closeFeedback();
    });
  }

  // Escape key closes feedback modal
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && $feedbackModal.classList.contains('open')) closeFeedback();
  });

  // Expose addBookmark for code viewer to use (bookmark current line)
  window._joltAddBookmark = addBookmark;
}
