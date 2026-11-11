/* ── Navigation History (Back/Forward) ─────────────── */

const MAX_HISTORY = 50;
let history = [];
let historyIndex = -1;

export function pushHistory(path) {
  if (!path) return;
  // Truncate forward entries if we're navigating back
  if (historyIndex < history.length - 1) {
    history = history.slice(0, historyIndex + 1);
  }
  // Skip consecutive duplicates
  if (history[history.length - 1] === path) return;

  history.push(path);
  if (history.length > MAX_HISTORY) history.shift();
  historyIndex = history.length - 1;
}

export function goBack() {
  if (historyIndex <= 0) return;
  historyIndex--;
  const path = history[historyIndex];
  import('./tabs.js').then(m => m.openTab(path));
}

export function goForward() {
  if (historyIndex >= history.length - 1) return;
  historyIndex++;
  const path = history[historyIndex];
  import('./tabs.js').then(m => m.openTab(path));
}

export function initNavHistory() {
  // Add back/forward buttons to breadcrumb bar
  const $bar = document.querySelector('.breadcrumb-bar');
  if (!$bar) return;

  const $back = document.createElement('button');
  $back.className = 'nav-btn';
  $back.title = 'Navigate back (Ctrl+Alt+\u2190)';
  $back.setAttribute('aria-label', 'Navigate back');
  $back.innerHTML = '&#x2190;';
  $back.addEventListener('click', goBack);

  const $fwd = document.createElement('button');
  $fwd.className = 'nav-btn';
  $fwd.title = 'Navigate forward (Ctrl+Alt+\u2192)';
  $fwd.setAttribute('aria-label', 'Navigate forward');
  $fwd.innerHTML = '&#x2192;';
  $fwd.addEventListener('click', goForward);

  // Insert before the breadcrumb div
  const $crumb = document.getElementById('breadcrumb');
  if ($crumb) {
    $bar.insertBefore($back, $crumb);
    $bar.insertBefore($fwd, $crumb);
  } else {
    $bar.prepend($back);
    $bar.prepend($fwd);
  }

  // Listen for tab activation events (dispatched by tabs.js)
  document.addEventListener('jolt:tab-activated', e => {
    pushHistory(e.detail.path);
  });
}
