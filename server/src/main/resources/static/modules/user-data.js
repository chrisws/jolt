const $bookmarks = document.getElementById('bookmark-list');
const $recent    = document.getElementById('recent-list');
const $feedbackModal = document.getElementById('feedback-modal');

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

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
  } catch (err) { console.error('Bookmarks load failed:', err); }
}

async function addBookmark(path, line, label) {
  try {
    await fetch('/api/bookmarks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path, line, label: label || path.split('/').pop() })
    });
    loadBookmarks();
  } catch (err) { console.error('Bookmark add failed:', err); }
}

async function deleteBookmark(bm) {
  try {
    await fetch('/api/bookmarks', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ path: bm.path, line: bm.line })
    });
    loadBookmarks();
  } catch (err) { console.error('Bookmark delete failed:', err); }
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
  } catch (err) { console.error('Recent load failed:', err); }
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
  } catch (err) { console.error('Search history failed:', err); }
}

function closeSearchHistory() {
  const $hist = document.getElementById('search-history');
  if ($hist) $hist.classList.remove('open');
}

// ── Feedback Modal ─────────────────────────────────────────

function openFeedback() {
  const $pageField = document.getElementById('fb-page');
  if ($pageField) $pageField.value = location.hash || '/';

  $feedbackModal.classList.add('open');

  const $msgField = document.getElementById('fb-message');
  if ($msgField) $msgField.focus();
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
    await fetch('/api/feedback', {
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
  } catch (err) { console.error('Feedback submit failed:', err); }
}

// ── Init ───────────────────────────────────────────────────

export function initUserData() {
  // Bookmarks + Recent in sidebar (load on start)
  loadBookmarks();
  loadRecent();

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

  // Feedback button in topbar
  const $fbBtn = document.getElementById('feedback-btn');
  if ($fbBtn) $fbBtn.addEventListener('click', openFeedback);

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