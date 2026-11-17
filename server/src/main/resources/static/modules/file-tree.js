const $fileTree = document.getElementById('file-tree');

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

function dirIcon() {
  return '<svg viewBox="0 0 16 16" width="16" height="16"><path d="M1 3h5l2 2h7v8a1 1 0 01-1 1H2a1 1 0 01-1-1V3z" fill="#e8a33d"/></svg>';
}

function fileIcon(type) {
  const c = { java:'#e76f00', javascript:'#f7df1e', typescript:'#3178c6', python:'#3572a5', html:'#e34c26', css:'#264de4' };
  const color = c[type] || '#808080';
  return '<svg viewBox="0 0 16 16" width="16" height="16"><rect x="2" y="2" width="12" height="12" rx="3" fill="' + color + '"/></svg>';
}

function toggleArrow(expanded) {
  return '<span class="tree-toggle' + (expanded ? ' expanded' : '') + '">&#x25B6;</span>';
}

// Cache for directory listings to avoid re-fetching
const dirCache = new Map();

async function loadDir(dirPath, target, depth) {
  try {
    let entries;
    if (dirCache.has(dirPath)) {
      entries = dirCache.get(dirPath);
    } else {
      const res = await fetch('/api/file/tree?path=' + encodeURIComponent(dirPath));
      if (!res.ok) throw new Error('HTTP ' + res.status);
      entries = await res.json();
      dirCache.set(dirPath, entries);
    }

    for (const e of entries) {
      const el = document.createElement('div');
      el.className = 'tree-item';
      el.style.paddingLeft = (8 + depth * 16) + 'px';

      if (e.type === 'directory') {
        el.setAttribute('role', 'treeitem');
        el.setAttribute('aria-expanded', 'false');
        el.setAttribute('tabindex', '-1');
        el.innerHTML = toggleArrow(false) + '<span class="icon">' + dirIcon() + '</span><span class="tree-label">' + esc(e.name) + '</span>';
        el.dataset.path = e.path;

        // Child container (hidden until expanded)
        const childContainer = document.createElement('div');
        childContainer.className = 'tree-children';
        childContainer.style.display = 'none';

        let loaded = false;

        el.addEventListener('click', async (ev) => {
          ev.stopPropagation();
          const isExpanded = childContainer.style.display !== 'none';

          if (!isExpanded && !loaded) {
            // First expand: fetch children into the container
            loaded = true;
            el.dataset.loaded = 'true';
            await loadDir(e.path, childContainer, depth + 1);
            markDirtyFiles();
          }

          childContainer.style.display = isExpanded ? 'none' : 'block';
          el.setAttribute('aria-expanded', !isExpanded ? 'true' : 'false');
          const arrow = el.querySelector('.tree-toggle');
          if (arrow) { arrow.classList.toggle('expanded', !isExpanded); }
        });

        target.appendChild(el);
        target.appendChild(childContainer);

      } else {
        el.setAttribute('role', 'treeitem');
        el.setAttribute('tabindex', '-1');
        const ext = e.name.split('.').pop();
        el.innerHTML = '<span class="tree-toggle-spacer"></span><span class="icon">' + fileIcon(ext) + '</span><span class="tree-label">' + esc(e.name) + '</span>';
        el.dataset.path = e.path;

        el.addEventListener('click', () => {
          $fileTree.querySelectorAll('.tree-item.active').forEach(a => a.classList.remove('active'));
          el.classList.add('active');
          import('./tabs.js').then(m => m.openTab(e.path));
        });

        target.appendChild(el);
      }
    }
  } catch (err) {
    const errEl = document.createElement('div');
    errEl.className = 'tree-item tree-error';
    errEl.style.paddingLeft = (8 + depth * 16) + 'px';
    errEl.setAttribute('role', 'treeitem');
    errEl.setAttribute('tabindex', '0');
    errEl.textContent = 'Failed to load — click to retry';
    errEl.addEventListener('click', () => { dirCache.delete(dirPath); loadDir(dirPath, target, depth); });
    target.appendChild(errEl);
  }
}

// ── Collapse / Expand All ──────────────────────────

function collapseAll() {
  $fileTree.querySelectorAll('.tree-children').forEach(c => { c.style.display = 'none'; });
  $fileTree.querySelectorAll('.tree-toggle').forEach(t => { t.classList.remove('expanded'); });
}

async function expandAll() {
  // Expand all directories, fetching children for those not yet loaded
  const dirs = $fileTree.querySelectorAll('.tree-item[data-path]');
  for (const dir of dirs) {
    const next = dir.nextElementSibling;
    if (next && next.classList.contains('tree-children')) {
      if (!dir.dataset.loaded) {
        dir.dataset.loaded = 'true';
        // Determine depth from padding-left
        const depth = Math.round((parseInt(dir.style.paddingLeft) - 8) / 16);
        await loadDir(dir.dataset.path, next, depth + 1);
      }
      next.style.display = 'block';
      const arrow = dir.querySelector('.tree-toggle');
      if (arrow) { arrow.classList.add('expanded'); }
    }
  }
}

/** Reveal a file in the tree: expand ancestor dirs lazily, scroll to it, highlight. */
export async function revealInTree(path) {
  const segments = path.split('/').filter(s => s.length > 0);
  if (segments.length === 0) return;

  // Ensure the Project section is open (accordion: collapse others)
  if ($fileTree.classList.contains('collapsed')) {
    document.querySelectorAll('.sidebar, .sidebar-section').forEach(s => {
      s.classList.add('collapsed');
      s.querySelector('.section-label')?.classList.add('collapsed');
    });
    $fileTree.classList.remove('collapsed');
    $fileTree.querySelector('.section-label')?.classList.remove('collapsed');
  }

  let container = $fileTree;

  for (let i = 0; i < segments.length; i++) {
    const currentPath = segments.slice(0, i + 1).join('/');
    const isLast = (i === segments.length - 1);

    // Find the matching tree-item in the current container's direct children
    let item = null;
    for (const el of container.children) {
      if (el.classList.contains('tree-item') && el.dataset.path === currentPath) {
        item = el;
        break;
      }
    }

    if (!item) return; // path not found in tree

    if (isLast) {
      // Target file: highlight and scroll into view
      $fileTree.querySelectorAll('.tree-item.active').forEach(a => a.classList.remove('active'));
      item.classList.add('active');
      item.classList.add('tree-reveal');
      item.scrollIntoView({ behavior: 'smooth', block: 'center' });
      setTimeout(() => item.classList.remove('tree-reveal'), 2000);
      return;
    }

    // Directory: ensure loaded and expanded, then descend into children
    const childContainer = item.nextElementSibling;
    if (!childContainer || !childContainer.classList.contains('tree-children')) return;

    if (!item.dataset.loaded) {
      item.dataset.loaded = 'true';
      const depth = Math.round((parseInt(item.style.paddingLeft, 10) - 8) / 16);
      await loadDir(currentPath, childContainer, depth + 1);
    }

    childContainer.style.display = 'block';
    const arrow = item.querySelector('.tree-toggle');
    if (arrow) arrow.classList.add('expanded');
    item.setAttribute('aria-expanded', 'true');

    container = childContainer;
  }
}

export async function loadFileTree() {
  $fileTree.innerHTML = '';

  // Section header with control buttons
  const header = document.createElement('div');
  header.className = 'section-label tree-header';
  header.innerHTML = '<span>Project</span><span class="tree-controls">' +
    '<button class="tree-btn" id="tree-sync" title="Sync with editor">&#x2316;</button>' +
    '<button class="tree-btn" id="tree-collapse-all" title="Collapse all">&#x2195;</button>' +
    '<button class="tree-btn" id="tree-expand-all" title="Expand all">&#x2194;</button></span>';

  $fileTree.appendChild(header);

  header.addEventListener('click', (e) => {
    if (e.target.closest('button')) return;
    const wasOpen = !$fileTree.classList.contains('collapsed');
    document.querySelectorAll('.sidebar, .sidebar-section').forEach(s => {
      s.classList.add('collapsed');
      s.querySelector('.section-label')?.classList.add('collapsed');
    });
    if (!wasOpen) {
      $fileTree.classList.remove('collapsed');
      header.classList.remove('collapsed');
    }
  });

  document.getElementById('tree-sync').addEventListener('click', () => {
    import('./tabs.js').then(m => {
      const tab = m.state.tabs.find(t => t.id === m.state.activeTab);
      if (tab && !tab.isGit) { revealInTree(tab.path); }
    });
  });
  document.getElementById('tree-collapse-all').addEventListener('click', collapseAll);
  document.getElementById('tree-expand-all').addEventListener('click', expandAll);

  await loadDir('', $fileTree, 0);
  markDirtyFiles();

  // ── Keyboard navigation ──────────────────────────
  $fileTree.addEventListener('keydown', (e) => {
    const items = Array.from($fileTree.querySelectorAll('.tree-item[tabindex]'));
    if (items.length === 0) return;
    const idx = items.findIndex(el => el === document.activeElement);

    switch (e.key) {
      case 'ArrowDown': {
        e.preventDefault();
        const next = items[idx + 1] || items[0];
        items.forEach(el => el.setAttribute('tabindex', '-1'));
        next.setAttribute('tabindex', '0');
        next.focus();
        break;
      }
      case 'ArrowUp': {
        e.preventDefault();
        const prev = items[idx - 1] || items[items.length - 1];
        items.forEach(el => el.setAttribute('tabindex', '-1'));
        prev.setAttribute('tabindex', '0');
        prev.focus();
        break;
      }
      case 'ArrowRight': {
        e.preventDefault();
        const el = document.activeElement;
        if (el && el.classList.contains('tree-item') && el.getAttribute('aria-expanded') === 'false') {
          el.click(); // expand directory
          el.setAttribute('aria-expanded', 'true');
        } else if (el && el.classList.contains('tree-item')) {
          // Move to first child if expanded
          const child = el.nextElementSibling;
          if (child && child.classList.contains('tree-children')) {
            const firstChild = child.querySelector('.tree-item');
            if (firstChild) { firstChild.setAttribute('tabindex', '0'); firstChild.focus(); }
          }
        }
        break;
      }
      case 'ArrowLeft': {
        e.preventDefault();
        const el = document.activeElement;
        if (el && el.classList.contains('tree-item') && el.getAttribute('aria-expanded') === 'true') {
          el.click(); // collapse directory
          el.setAttribute('aria-expanded', 'false');
        } else if (el && el.classList.contains('tree-item')) {
          // Move to parent (find previous item with less padding)
          const pad = parseInt(el.style.paddingLeft) || 8;
          for (let i = idx - 1; i >= 0; i--) {
            const pPad = parseInt(items[i].style.paddingLeft) || 8;
            if (pPad < pad) { items.forEach(x => x.setAttribute('tabindex', '-1')); items[i].setAttribute('tabindex', '0'); items[i].focus(); break; }
          }
        }
        break;
      }
      case 'Enter': {
        e.preventDefault();
        const el = document.activeElement;
        if (el && el.classList.contains('tree-item')) el.click();
        break;
      }
    }
  });

  // Set initial tabindex on first item for keyboard access
  const firstItem = $fileTree.querySelector('.tree-item');
  if (firstItem) firstItem.setAttribute('tabindex', '0');
}

/** Fetch git status and mark dirty files in the tree with a dot indicator. */
export async function markDirtyFiles() {
  try {
    const res = await fetch('/api/git/status');
    if (!res.ok) return;
    const entries = await res.json(); // array of {path, type} objects

    const dirtySet = new Set(entries.map(e => e.path));

    $fileTree.querySelectorAll('.tree-item[data-path]').forEach(el => {
      const path = el.dataset.path;
      if (dirtySet.has(path)) {
        el.classList.add('dirty');
        const label = el.querySelector('.tree-label');
        if (label && !label.querySelector('.dirty-dot')) {
          const dot = document.createElement('span');
          dot.className = 'dirty-dot';
          dot.title = 'View diff';
          dot.addEventListener('click', (ev) => {
            ev.stopPropagation();
            import('./diff-panel.js').then(m => m.openDiff(path));
          });
          label.appendChild(dot);
        }
      }
    });
  } catch (_) { /* not a git repo or API unavailable */ }
}

// ── Keyboard navigation for tree ──────────────────
$fileTree.addEventListener('keydown', e => {
  const items = Array.from($fileTree.querySelectorAll('.tree-item[role="treeitem"]'));
  const idx = items.findIndex(el => el.getAttribute('tabindex') === '0');
  if (idx === -1) return;

  let next = -1;
  switch (e.key) {
    case 'ArrowDown': next = Math.min(idx + 1, items.length - 1); break;
    case 'ArrowUp':   next = Math.max(idx - 1, 0); break;
    case 'ArrowRight': {
      const item = items[idx];
      if (item.getAttribute('aria-expanded') === 'false') {
        item.click(); // expand
      } else {
        // Move to first child
        const children = item.nextElementSibling;
        if (children && children.classList.contains('tree-children')) {
          const firstChild = children.querySelector('.tree-item[role="treeitem"]');
          if (firstChild) next = items.indexOf(firstChild);
        }
      }
      break;
    }
    case 'ArrowLeft': {
      const item = items[idx];
      if (item.getAttribute('aria-expanded') === 'true') {
        item.click(); // collapse
      } else {
        // Move to parent (find the enclosing tree-children)
        let parent = item.parentElement;
        while (parent && parent !== $fileTree) {
          if (parent.classList.contains('tree-children')) {
            const prevItem = parent.previousElementSibling;
            if (prevItem && prevItem.classList.contains('tree-item')) { next = items.indexOf(prevItem); break; }
          }
          parent = parent.parentElement;
        }
      }
      break;
    }
    case 'Enter': case ' ': items[idx].click(); e.preventDefault(); return;
    default: return;
  }

  if (next !== -1) {
    e.preventDefault();
    items[idx].setAttribute('tabindex', '-1');
    items[next].setAttribute('tabindex', '0');
    items[next].focus();
  } else if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
    e.preventDefault(); // consumed even if no navigation target
  }
});

