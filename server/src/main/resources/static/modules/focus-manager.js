/* ── Coordinated Escape + Focus Management ────────────────── */

// Track the element that triggered a panel/modal open, so we can return focus on close
let _focusReturnTarget = null;

export function setFocusReturn(el) { _focusReturnTarget = el; }
export function getFocusReturn() { return _focusReturnTarget; }
export function clearFocusReturn() { _focusReturnTarget = null; }

/** Close the topmost open UI element (modal > panel > dropdown). */
export function closeTopmost() {
  // 1. Modals (highest priority)
  const $feedback = document.getElementById('feedback-modal');
  if ($feedback && $feedback.classList.contains('open')) {
    $feedback.classList.remove('open');
    restoreFocus();
    return true;
  }

  const $qo = document.getElementById('qo-backdrop');
  if ($qo && $qo.classList.contains('open')) {
    import('./quick-open.js').then(m => m.closeQuickOpen());
    restoreFocus();
    return true;
  }

  // 2. Panels (search, settings, inspector) — close the last opened one
  const panels = [
    document.getElementById('inspector-panel'),
    document.getElementById('settings-panel'),
    document.getElementById('search-panel'),
  ];

  for (const panel of panels) {
    if (panel && panel.classList.contains('open')) {
      panel.classList.remove('open');
      panel.setAttribute('aria-hidden', 'true');
      panel.setAttribute('inert', '');
      restoreFocus();
      return true;
    }
  }

  // 3. Dropdowns (branch selector, suggest)
  const $branchDD = document.getElementById('branch-dropdown');
  if ($branchDD && $branchDD.classList.contains('open')) {
    $branchDD.classList.remove('open');
    const $sel = document.getElementById('branch-selector');
    if ($sel) $sel.setAttribute('aria-expanded', 'false');
    return true;
  }

  const $suggest = document.getElementById('suggest-dropdown');
  if ($suggest && $suggest.classList.contains('open')) {
    $suggest.classList.remove('open');
  }

  return false;
}

function restoreFocus() {
  if (_focusReturnTarget && _focusReturnTarget.isConnected) {
    _focusReturnTarget.focus();
  } else {
    const $main = document.getElementById('main-content');
    if ($main) $main.focus();
  }
  clearFocusReturn();
}

// Global Escape handler — capture phase to run before keybinding engine
if (typeof document !== 'undefined') {
  document.addEventListener('keydown', e => {
    if (e.key !== 'Escape') return;

    const tag = e.target.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA') {
      const $feedback = document.getElementById('feedback-modal');
      const $qo = document.getElementById('qo-backdrop');
      if (($feedback && $feedback.classList.contains('open')) || ($qo && $qo.classList.contains('open'))) {
        e.preventDefault();
        closeTopmost();
      }
      return;
    }

    e.preventDefault();
    closeTopmost();
  }, true);
}

