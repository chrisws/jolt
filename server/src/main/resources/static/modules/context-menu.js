const $menu = document.createElement('div');
$menu.className = 'context-menu';
$menu.style.display = 'none';
document.body.appendChild($menu);

let activeTarget = null;

function showMenu(x, y, items) {
  $menu.innerHTML = '';
  for (const item of items) {
    const el = document.createElement('div');
    el.className = 'context-menu-item';
    el.textContent = item.label;
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      hideMenu();
      item.action();
    });
    $menu.appendChild(el);
  }

  // Position: keep within viewport
  const rect = $menu.getBoundingClientRect();
  const menuW = 180;
  const menuH = items.length * 32;
  let px = x;
  let py = y;
  if (px + menuW > window.innerWidth) px = window.innerWidth - menuW - 4;
  if (py + menuH > window.innerHeight) py = window.innerHeight - menuH - 4;

  $menu.style.left = px + 'px';
  $menu.style.top = py + 'px';
  $menu.style.display = 'block';
}

function hideMenu() {
  $menu.style.display = 'none';
  activeTarget = null;
}

function copyToClipboard(text) {
  navigator.clipboard.writeText(text).catch(() => {});
}

export function initContextMenu() {

  // ── File tree context menu ──────────────────
  const $tree = document.getElementById('file-tree');
  if ($tree) {
    $tree.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      const item = e.target.closest('.tree-item');
      if (!item || !item.dataset.path) { hideMenu(); return; }

      activeTarget = item;
      const path = item.dataset.path;

      const items = [
        { label: 'Copy Path', action: () => copyToClipboard(path) },
        { label: 'Open in Tab', action: () => import('./tabs.js').then(m => m.openTab(path)) },
      ];

      // Only show Bookmark for files (non-directories)
      if (!item.querySelector('.tree-toggle')) {
        items.push({ label: 'Bookmark', action: () => import('./user-data.js').then(m => m.addBookmark(path)) });
      }

      showMenu(e.clientX, e.clientY, items);
    });
  }

  // ── Code viewer context menu ────────────────
  const $viewer = document.getElementById('code-viewer');
  if ($viewer) {
    $viewer.addEventListener('contextmenu', (e) => {
      e.preventDefault();

      const items = [
        { label: 'Copy Selection', action: () => {
          const sel = window.getSelection();
          if (sel.toString()) copyToClipboard(sel.toString());
        }},
        { label: 'Copy All', action: () => {
          copyToClipboard($viewer.innerText);
        }},
      ];

      showMenu(e.clientX, e.clientY, items);
    });
  }

  // ── Global dismiss ──────────────────────────
  document.addEventListener('click', hideMenu);
  document.addEventListener('contextmenu', (e) => {
    if (!e.target.closest('.tree-item') && !e.target.closest('#code-viewer')) {
      hideMenu();
    }
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') hideMenu(); });

  // Scroll also dismisses
  window.addEventListener('scroll', hideMenu, true);
}
