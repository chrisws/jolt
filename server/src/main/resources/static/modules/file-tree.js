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

async function loadDir(dirPath, parentEl) {
  try {
    const res = await fetch('/api/file/tree?path=' + encodeURIComponent(dirPath));
    const entries = await res.json();

    for (const e of entries) {
      const el = document.createElement('div');
      el.className = 'tree-item';
      el.style.paddingLeft = (12 + (parentEl ? parentEl.dataset.depth : 0) * 16) + 'px';

      if (e.type === 'directory') {
        el.innerHTML = '<span class="icon">' + dirIcon() + '</span><span>' + esc(e.name) + '</span>';
        el.dataset.depth = parentEl ? Number(parentEl.dataset.depth) + 1 : 1;
        el.addEventListener('click', () => {
          if (el.dataset.loaded) { el.nextElementSibling && (el.nextElementSibling.style.display = none); el.dataset.loaded = '0'; return; }
          const container = document.createElement('div');
          container.style.display = 'block';
          el.after(container);
          loadDir(e.path, el);
          el.dataset.loaded = '1';
        });
      } else {
        const ext = e.name.split('.').pop();
        el.innerHTML = '<span class="icon">' + fileIcon(ext) + '</span><span>' + esc(e.name) + '</span>';
        el.addEventListener('click', () => {
          $fileTree.querySelectorAll('.tree-item.active').forEach(a => a.classList.remove('active'));
          el.classList.add('active');
          import('./tabs.js').then(m => m.openTab(e.path));
        });
      }

      if (parentEl && parentEl.nextElementSibling) parentEl.nextElementSibling.appendChild(el);
      else parentEl.appendChild(el);
    }
  } catch (err) { console.error('Dir load failed:', err); }
}

export function loadFileTree() {
  const label = $fileTree.querySelector('.section-label');
  $fileTree.innerHTML = '';
  $fileTree.appendChild(label);

  const root = document.createElement('div');
  root.className = 'tree-item';
  root.dataset.depth = '0';
  root.innerHTML = '<span class="icon">' + dirIcon() + '</span><span>src</span>';
  root.addEventListener('click', () => { loadDir('', root); });
  $fileTree.appendChild(root);

  loadDir('', null);
}
