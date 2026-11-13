/* ── Quick Open (Ctrl+P) ─────────────────────────────── */

const $qoBackdrop = document.getElementById('qo-backdrop');
const $qoInput   = document.getElementById('qo-input');
const $qoList    = document.getElementById('qo-list');

let qoItems       = [];
let qoActive      = -1;
let qoTimer       = null;
let qoOpen        = false;

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

let _focusTrapHandler = null;

function setupFocusTrap() {
  _focusTrapHandler = e => {
    if (e.key !== 'Tab') return;
    const dialog = $qoBackdrop.querySelector('.qo-dialog');
    const focusable = dialog.querySelectorAll('input, button, [tabindex]:not([tabindex="-1"])');
    if (focusable.length === 0) return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    } else if (!dialog.contains(document.activeElement)) {
      e.preventDefault();
      first.focus();
    }
  };
  document.addEventListener('keydown', _focusTrapHandler);
}

function teardownFocusTrap() {
  if (_focusTrapHandler) {
    document.removeEventListener('keydown', _focusTrapHandler);
    _focusTrapHandler = null;
  }
}

export function openQuickOpen() {
  qoOpen = true;
  qoItems = [];
  qoActive = -1;
  $qoInput.value = '';
  $qoList.innerHTML = '';
  $qoBackdrop.classList.add('open');
  setupFocusTrap();
  $qoInput.focus();
}

export function closeQuickOpen() {
  qoOpen = false;
  $qoBackdrop.classList.remove('open');
  teardownFocusTrap();
}

/* ── Recent Files (Ctrl+E) ─────────────────────────── */

export async function openRecent() {
  qoOpen = true;
  qoItems = [];
  qoActive = -1;
  $qoInput.value = '';
  $qoList.innerHTML = '<div class="qo-empty">Loading…</div>';
  $qoBackdrop.classList.add('open');
  $qoInput.focus();

  try {
    const res = await fetch('/api/recent');
    const files = await res.json();

    $qoList.innerHTML = '';

    if (!files || files.length === 0) {
      $qoList.innerHTML = '<div class="qo-empty">No recent files</div>';
      return;
    }

    const header = document.createElement('div');
    header.className = 'qo-group-header';
    header.textContent = 'recent';
    $qoList.appendChild(header);

    for (const f of files) {
      const el = document.createElement('div');
      el.className = 'qo-item';
      const name = f.path.split('/').pop();
      const dir = f.path.substring(0, f.path.lastIndexOf('/'));
      el.innerHTML = '<span class="qo-kind qk-file">F</span>' +
        '<span class="qo-name">' + esc(name) + '</span>' +
        '<span class="qo-path">' + esc(dir) + '</span>';
      el.addEventListener('click', () => {
        closeQuickOpen();
        import('./tabs.js').then(m => m.openTab(f.path));
      });
      $qoList.appendChild(el);
      qoItems.push({ hit: { filePath: f.path, name, kind: 'FILE' }, el });
    }

    scrollToActive();
  } catch (err) {
    $qoList.innerHTML = '<div class="qo-empty">Failed to load</div>';
    console.error('Recent files failed:', err);
  }
}

async function doQuickSearch(query) {
  if (!query || query.length < 1) { $qoList.innerHTML = ''; qoItems = []; return; }

  try {
    const res = await fetch('/api/search/suggest?q=' + encodeURIComponent(query) + '&limit=15');
    const results = await res.json();
    renderQuickResults(results);
  } catch (err) { console.error('Quick Open search failed:', err); }
}

function renderQuickResults(results) {
  $qoList.innerHTML = '';
  qoItems = [];
  qoActive = -1;

  if (!results || results.length === 0) {
    $qoList.innerHTML = '<div class="qo-empty">No matches</div>';
    return;
  }

  // Group by kind for visual separation
  const groups = {};
  for (const r of results) {
    const kind = r.kind || 'FILE';
    if (!groups[kind]) groups[kind] = [];
    groups[kind].push(r);
  }

  const order = ['FILE', 'CLASS', 'METHOD', 'FIELD'];
  for (const kind of order) {
    const items = groups[kind];
    if (!items || items.length === 0) continue;

    const header = document.createElement('div');
    header.className = 'qo-group-header';
    header.textContent = kind.toLowerCase() + 's';
    $qoList.appendChild(header);

    for (const r of items) {
      const el = document.createElement('div');
      el.className = 'qo-item';
      el.innerHTML = '<span class="qo-kind qk-' + esc(kind.toLowerCase()) + '">' + esc(kind.charAt(0)) + '</span>' +
        '<span class="qo-name">' + esc(r.name || r.qualifiedName || '') + '</span>' +
        '<span class="qo-path">' + esc(r.filePath || '') + '</span>';
      el.addEventListener('click', () => selectQuickItem(r));
      $qoList.appendChild(el);
      qoItems.push({ hit: r, el });
    }
  }

  scrollToActive();
}

function selectQuickItem(hit) {
  closeQuickOpen();
  import('./tabs.js').then(m => m.openTab(hit.filePath));
}

function moveActive(dir) {
  const navigable = qoItems.filter(item => item.el.classList.contains('qo-item'));
  if (navigable.length === 0) return;

  qoActive = (qoActive + dir + navigable.length) % navigable.length;
  navigable.forEach((item, i) => item.el.classList.toggle('active', i === qoActive));
  scrollToActive();
}

function scrollToActive() {
  if (qoActive < 0) return;
  const navigable = qoItems.filter(item => item.el.classList.contains('qo-item'));
  if (navigable[qoActive]) navigable[qoActive].el.scrollIntoView({ block: 'nearest' });
}

export function initQuickOpen() {
  $qoInput.addEventListener('input', () => {
    clearTimeout(qoTimer);
    qoTimer = setTimeout(() => doQuickSearch($qoInput.value), 150);
  });

  $qoInput.addEventListener('keydown', e => {
    if (e.key === 'ArrowDown') { e.preventDefault(); moveActive(1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); moveActive(-1); }
    else if (e.key === 'Enter') {
      e.preventDefault();
      const navigable = qoItems.filter(item => item.el.classList.contains('qo-item'));
      if (qoActive >= 0 && navigable[qoActive]) selectQuickItem(navigable[qoActive].hit);
    }
    else if (e.key === 'Escape') { closeQuickOpen(); }
  });

  $qoBackdrop.addEventListener('click', e => {
    if (e.target === $qoBackdrop) closeQuickOpen();
  });
}

