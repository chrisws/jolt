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

async function loadDir(dirPath, target, depth) {
  try {
    const res = await fetch('/api/file/tree?path=' + encodeURIComponent(dirPath));
    const entries = await res.json();

    for (const e of entries) {
      const el = document.createElement('div');
      el.className = 'tree-item';
      el.style.paddingLeft = (8 + depth * 16) + 'px';

      if (e.type === 'directory') {
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
            await loadDir(e.path, childContainer, depth + 1);
          }

          childContainer.style.display = isExpanded ? 'none' : 'block';
          const arrow = el.querySelector('.tree-toggle');
          if (arrow) { arrow.classList.toggle('expanded', !isExpanded); }
        });

        target.appendChild(el);
        target.appendChild(childContainer);

      } else {
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
  } catch (err) { console.error('Dir load failed:', err); }
}

// ── Collapse / Expand All ──────────────────────────

function collapseAll() {
  $fileTree.querySelectorAll('.tree-children').forEach(c => { c.style.display = 'none'; });
  $fileTree.querySelectorAll('.tree-toggle').forEach(t => { t.classList.remove('expanded'); });
}

function expandAll() {
  // Expand all directories that already have loaded children
  const dirs = $fileTree.querySelectorAll('.tree-item[data-path]');
  dirs.forEach(dir => {
    const next = dir.nextElementSibling;
    if (next && next.classList.contains('tree-children')) {
      next.style.display = 'block';
      const arrow = dir.querySelector('.tree-toggle');
      if (arrow) { arrow.classList.add('expanded'); }
    }
  });
}

export async function loadFileTree() {
  $fileTree.innerHTML = '';

  // Section header with control buttons
  const header = document.createElement('div');
  header.className = 'section-label tree-header';
  header.innerHTML = '<span>Project</span><span class="tree-controls"><button class="tree-btn" id="tree-collapse-all" title="Collapse all">&#x2195;</button><button class="tree-btn" id="tree-expand-all" title="Expand all">&#x2194;</button></span>';

  $fileTree.appendChild(header);

  document.getElementById('tree-collapse-all').addEventListener('click', collapseAll);
  document.getElementById('tree-expand-all').addEventListener('click', expandAll);

  await loadDir('', $fileTree, 0);
  markDirtyFiles();
}

/** Fetch git status and mark dirty files in the tree with a dot indicator. */
async function markDirtyFiles() {
  try {
    const res = await fetch('/api/git/status');
    if (!res.ok) return;
    const dirtyPaths = await res.json(); // array of file paths

    $fileTree.querySelectorAll('.tree-item[data-path]').forEach(el => {
      const path = el.dataset.path;
      if (dirtyPaths.includes(path)) {
        el.classList.add('dirty');
        const label = el.querySelector('.tree-label');
        if (label && !label.querySelector('.dirty-dot')) {
          const dot = document.createElement('span');
          dot.className = 'dirty-dot';
          dot.title = 'Uncommitted changes';
          label.appendChild(dot);
        }
      }
    });
  } catch (_) { /* not a git repo or API unavailable */ }
}
