const $viewer  = document.getElementById('code-viewer');
const $crumb   = document.getElementById('breadcrumb');

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

export async function loadFile(path) {
  try {
    const res = await fetch('/api/file?path=' + encodeURIComponent(path));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();
    renderCode(data.content, data.language);
    renderBreadcrumb(path);
    $viewer.focus();
  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

export function renderCode(content, language) {
  const lines = content.split('\n');
  let html = '';
  for (let i = 0; i < lines.length; i++) {
    html += '<div class="code-line" data-line="' + (i + 1) + '">';
    html += '<span class="line-num">' + (i + 1) + '</span>';
    html += '<span class="line-content">' + esc(lines[i]) + '</span>';
    html += '</div>';
  }
  $viewer.innerHTML = html;

  if (language && hljs.getLanguage(language)) {
    $viewer.querySelectorAll('.line-content').forEach((el, i) => {
      try { el.innerHTML = hljs.highlight(lines[i], { language }).value; } catch (_) {}
    });
  }
  $viewer.scrollTop = 0;
}

export function renderBreadcrumb(path) {
  const parts = path.split('/');
  let html = '';
  parts.forEach((part, i) => {
    if (i > 0) html += '<span class="sep">/</span>';
    const sub = parts.slice(0, i + 1).join('/');
    // Only file-like segments (contain a dot) are clickable
    const isFile = part.includes('.');
    html += isFile
      ? '<span data-path="' + esc(sub) + '" class="crumb-file">' + esc(part) + '</span>'
      : '<span class="crumb-dir">' + esc(part) + '</span>';
  });
  $crumb.innerHTML = html;
  $crumb.querySelectorAll('span.crumb-file').forEach(el => {
    el.addEventListener('click', () => { if (el.dataset.path !== path) import('./tabs.js').then(m => m.openTab(el.dataset.path)); });
  });
}

/* ── Git Blame Toggle ─────────────────────────── */

let blameActive = false;

export async function toggleBlame(filePath) {
  blameActive = !blameActive;

  const $btn = document.getElementById('blame-toggle');
  if ($btn) $btn.classList.toggle('active', blameActive);

  if (!blameActive) { removeBlameGutter(); return; }

  try {
    const res = await fetch('/api/blame?path=' + encodeURIComponent(filePath));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const lines = await res.json();

    const blameMap = {};
    for (const bl of lines) { blameMap[bl.line] = bl; }

    $viewer.querySelectorAll('.code-line').forEach(el => {
      const ln = Number(el.dataset.line);
      const bl = blameMap[ln];
      if (bl) {
        const span = document.createElement('span');
        span.className = 'blame-info';
        span.title = bl.author + ' — ' + bl.date + '\n' + bl.hash + ' ' + bl.summary;
        span.textContent = bl.author.split(' ')[0] + ' ' + (bl.date || '').slice(0, 10);
        el.insertBefore(span, el.querySelector('.line-num'));
      }
    });
  } catch (err) { console.error('Blame failed:', err); }
}

function removeBlameGutter() {
  $viewer.querySelectorAll('.blame-info').forEach(el => el.remove());
}

/* ── Go to Line ──────────────────────────────────── */

export function gotoLine(n) {
  const el = $viewer.querySelector('.code-line[data-line="' + n + '"]');
  if (!el) return;

  el.scrollIntoView({ block: 'center', behavior: 'smooth' });

  // Flash highlight
  el.classList.add('line-flash');
  setTimeout(() => el.classList.remove('line-flash'), 1200);
}

export function initFileViewer() {
  const $btn = document.getElementById('blame-toggle');
  if ($btn) {
    $btn.addEventListener('click', () => {
      import('./tabs.js').then(tabs => {
        const path = tabs.state.activeTab;
        if (path) toggleBlame(path);
      });
    });
  }

  // Clear blame gutter when switching files
  const origLoad = loadFile;
  window._joltClearBlame = removeBlameGutter;
}
