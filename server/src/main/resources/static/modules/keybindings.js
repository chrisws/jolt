/* ── Keybinding Engine ─────────────────────────────── */

const actions = {};
let sequenceBuffer = [];
let sequenceTimer = null;
const SEQ_TIMEOUT = 1000; // ms

let vimMode = 'normal'; // normal | insert | visual

/* ── Binding string parser ───────────────────────── */

export function parseBinding(str) {
  const tokens = [];
  const parts = str.split(/\s+/);

  for (const part of parts) {
    if (!part) continue;

    // Handle bracket sequences like [d] → [, d, ]
    if (part.startsWith('[') && part.endsWith(']')) {
      for (const ch of part) tokens.push({ key: ch });
      continue;
    }

    // Handle modifier+key like C-x, M-f, Alt+f (single-char key only)
    const modMatch = part.match(/^(Ctrl|C|M|Alt)\+?(-|\+)?([a-z0-9])$/i);
    if (modMatch) {
      let mod = modMatch[1].toLowerCase();
      if (mod === 'c') mod = 'ctrl';
      if (mod === 'm') mod = 'alt';
      const key = modMatch[3].toLowerCase();
      tokens.push({ mod, key });
      continue;
    }

    // Handle multi-modifier like Ctrl+Shift+F
    const plusParts = part.split('+');
    if (plusParts.length > 1) {
      const mods = [];
      let key = '';
      for (const p of plusParts) {
        const pl = p.toLowerCase();
        if (pl === 'ctrl' || pl === 'shift' || pl === 'alt' || pl === 'meta') mods.push(pl);
        else key = pl;
      }
      if (key) tokens.push({ mod: mods.join('+'), key });
      continue;
    }

    // Plain character(s) — each char is a separate token for sequences like "gg"
    for (const ch of part) {
      tokens.push({ key: ch.toLowerCase() });
    }
  }

  return tokens;
}

/* ── Token matching ──────────────────────────────── */

export function tokenMatches(token, e) {
  const key = e.key.toLowerCase();

  if (token.mod) {
    const requiredMods = token.mod.split('+');
    for (const m of requiredMods) {
      if (m === 'ctrl' && !e.ctrlKey) return false;
      if (m === 'shift' && !e.shiftKey) return false;
      if (m === 'alt' && !e.altKey) return false;
      if (m === 'meta' && !e.metaKey) return false;
    }
  } else {
    if (e.ctrlKey || e.altKey || e.metaKey) return false;
  }

  return key === token.key;
}

/* ── Sequence matching ───────────────────────────── */

function tryMatchSequence(e, bindings) {
  sequenceBuffer.push(e);

  clearTimeout(sequenceTimer);
  sequenceTimer = setTimeout(() => { sequenceBuffer = []; }, SEQ_TIMEOUT);

  const sorted = [...bindings].sort((a, b) => b.tokens.length - a.tokens.length);

  for (const binding of sorted) {
    if (binding.tokens.length > sequenceBuffer.length) continue;

    const relevant = sequenceBuffer.slice(-binding.tokens.length);
    let matched = true;

    for (let i = 0; i < binding.tokens.length; i++) {
      if (!tokenMatches(binding.tokens[i], relevant[i])) { matched = false; break; }
    }

    if (matched) {
      sequenceBuffer = [];
      clearTimeout(sequenceTimer);
      dispatch(binding.action);
      return true;
    }
  }

  return false;
}

/* ── Action registry ─────────────────────────────── */

export function registerAction(name, fn) { actions[name] = fn; }

function dispatch(name) {
  if (actions[name]) actions[name]();
}

/* ── Vim mode tracking ───────────────────────────── */

function handleVimMode(e) {
  if (vimMode === 'insert' && e.key === 'Escape') { vimMode = 'normal'; return true; }

  if (vimMode === 'normal') {
    if (e.key === 'i' && !e.ctrlKey && !e.altKey && !e.metaKey) { vimMode = 'insert'; return true; }
    if (e.key === 'v' && !e.ctrlKey && !e.altKey && !e.metaKey) { vimMode = 'visual'; return true; }
  }

  if (vimMode === 'visual' && e.key === 'Escape') { vimMode = 'normal'; return true; }

  return false;
}

/* ── Init ────────────────────────────────────────── */

export async function initKeybindings() {
  let mode = 'default';
  try {
    const res = await fetch('/api/settings');
    const s = await res.json();
    mode = s['keybindings.mode'] || 'default';
  } catch (_) {}

  let bindings = [];
  try {
    const res = await fetch('/api/keybindings?mode=' + encodeURIComponent(mode));
    const data = await res.json();
    // API returns { contexts: {...}, flat: { "Ctrl+Shift+F": "focus_search", ... } }
    // Transform to flat array: [{ binding, action }, ...]
    bindings = Object.entries(data.flat || {}).map(([keys, action]) => ({ binding: keys, action }));
  } catch (err) { console.error('Keybindings load failed:', err); return; }

  for (const b of bindings) { b.tokens = parseBinding(b.binding); }

  // Wire up actions using dynamic imports to avoid circular deps
  registerAction('focus_search', () => import('./search.js').then(m => m.focusSearch()));
  registerAction('close_panel', () => { import('./search.js').then(m => m.closeSearch()); import('./settings.js').then(m => m.closeSettings()); import('./inspector.js').then(m => m.closeInspector()); });
  registerAction('close_tab', () => import('./tabs.js').then(m => { if (m.state.activeTab) m.closeTab(m.state.activeTab); }));
  registerAction('next_tab', () => import('./tabs.js').then(m => cycleTab(m, 1)));
  registerAction('prev_tab', () => import('./tabs.js').then(m => cycleTab(m, -1)));
  registerAction('open_git_log', () => { location.hash = '#/git/commits'; });

  // Navigation actions — open inspector panel with results
  registerAction('go_to_definition', () => { getActiveSymbol().then(s => { if (s) import('./inspector.js').then(m => m.showDefinition(s)); }); });
  registerAction('find_references', () => { getActiveSymbol().then(s => { if (s) import('./inspector.js').then(m => m.showReferences(s)); }); });
  registerAction('show_hierarchy', () => { getActiveSymbol().then(s => { if (s) import('./inspector.js').then(m => m.showHierarchy(s)); }); });
  registerAction('show_call_graph', () => { getActiveSymbol().then(s => { if (s) import('./inspector.js').then(m => m.showCallGraph(s, 3)); }); });
  registerAction('who_calls_me', () => { getActiveSymbol().then(s => { if (s) import('./inspector.js').then(m => m.showCallers(s)); }); });

  // Parser panels — file-specific (use active tab path)
  registerAction('parse_jsp', () => { getActivePath().then(p => { if (p) import('./inspector.js').then(m => m.showJspParse(p)); }); });
  registerAction('parse_spring', () => { getActivePath().then(p => { if (p) import('./inspector.js').then(m => m.showSpringBeans(p)); }); });

  // Parser panels — project-wide J2EE
  registerAction('j2ee_webxml', () => import('./inspector.js').then(m => m.showJ2eeWebXml()));
  registerAction('j2ee_persistence', () => import('./inspector.js').then(m => m.showJ2eePersistence()));
  registerAction('j2ee_annotations', () => import('./inspector.js').then(m => m.showJ2eeAnnotations()));
  registerAction('j2ee_wiring_graph', () => import('./inspector.js').then(m => m.showJ2eeWiringGraph()));

  registerAction('quick_file_open', () => { if (window._openQuickOpen) window._openQuickOpen(); });

  // Go to Line (Ctrl+G)
  registerAction('go_to_line', () => {
    const line = prompt('Go to line:');
    if (line === null) return;
    const n = parseInt(line, 10);
    if (isNaN(n) || n < 1) return;
    import('./file-viewer.js').then(m => m.gotoLine(n));
  });

  // Recent Files (Ctrl+E)
  registerAction('open_recent', () => {
    import('./quick-open.js').then(m => m.openRecent());
  });

  // Navigation History (Ctrl+Alt+Left / Ctrl+Alt+Right)
  registerAction('nav_back', () => { import('./nav-history.js').then(m => m.goBack()); });
  registerAction('nav_forward', () => { import('./nav-history.js').then(m => m.goForward()); });

  // File Structure (Ctrl+F12)
  registerAction('show_structure', () => { getActivePath().then(p => { if (p) import('./inspector.js').then(m => m.showFileStructure(p)); }); });

  // Go to Implementation (Ctrl+Alt+B)
  registerAction('go_to_implementation', () => { getActiveSymbol().then(s => { if (s) import('./inspector.js').then(m => m.showImplementations(s)); }); });

  // Super Method (Ctrl+U)
  registerAction('super_method', () => { getActiveSymbol().then(s => { if (s) import('./inspector.js').then(m => m.showSuperMethod(s, s)); }); });

  // Toggle Bookmark (Ctrl+D) — bookmark first visible line in active file
  registerAction('toggle_bookmark', () => {
    getActivePath().then(p => {
      if (!p || !window._joltAddBookmark) return;
      const $viewer = document.getElementById('code-viewer');
      const lines = $viewer.querySelectorAll('.code-line');
      if (!lines.length) return;
      // Find first visible line in viewport
      const top = $viewer.scrollTop;
      let currentLine = lines[0].dataset.line;
      for (const el of lines) {
        if (el.offsetTop >= top) { currentLine = el.dataset.line; break; }
      }
      window._joltAddBookmark(p, parseInt(currentLine, 10));
    });
  });

  document.addEventListener('keydown', e => {
    const tag = e.target.tagName;
    if ((tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') && e.key !== 'Escape') return;

    if (handleVimMode(e)) { e.preventDefault(); return; }

    if (tryMatchSequence(e, bindings)) { e.preventDefault(); }
  });
}

function cycleTab(tabsModule, dir) {
  const { state, activateTab } = tabsModule;
  if (state.tabs.length < 2) return;
  const idx = state.tabs.findIndex(t => t.id === state.activeTab);
  if (idx === -1) return;
  const next = state.tabs[(idx + dir + state.tabs.length) % state.tabs.length];
  activateTab(next.id);
}


/* ── Get symbol under cursor (from active tab file) ── */

function getActiveSymbol() {
  return import('./tabs.js').then(tabs => {
    const path = tabs.state.activeTab;
    if (!path) return null;
    // Extract filename as a best-effort symbol hint
    // Full symbol extraction would need AST — for now use filename without extension
    const name = path.split('/').pop().replace(/\.[^.]+$/, '');
    return name || null;
  });
}

/* ── Get active tab file path ─────────────────────── */

function getActivePath() {
  return import('./tabs.js').then(tabs => {
    return tabs.state.activeTab || null;
  });
}
