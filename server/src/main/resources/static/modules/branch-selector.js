const $selector = document.getElementById('branch-selector');
const $name = document.getElementById('branch-name');
const $dropdown = document.getElementById('branch-dropdown');

export function initBranchSelector() {
  loadBranches();
  $selector.addEventListener('click', toggleDropdown);

  // Keyboard: Enter/Space to open, Escape to close, ArrowUp/Down + Enter to select
  $selector.addEventListener('keydown', e => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      toggleDropdown();
    } else if (e.key === 'Escape') {
      closeDropdown();
    } else if ($dropdown.classList.contains('open')) {
      const items = Array.from($dropdown.querySelectorAll('.branch-dropdown-item'));
      const activeIdx = items.findIndex(i => i.classList.contains('focused'));

      if (e.key === 'ArrowDown') {
        e.preventDefault();
        const next = activeIdx < items.length - 1 ? activeIdx + 1 : 0;
        items.forEach((i, idx) => i.classList.toggle('focused', idx === next));
        items[next].scrollIntoView({ block: 'nearest' });
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        const prev = activeIdx > 0 ? activeIdx - 1 : items.length - 1;
        items.forEach((i, idx) => i.classList.toggle('focused', idx === prev));
        items[prev].scrollIntoView({ block: 'nearest' });
      } else if (e.key === 'Enter' && activeIdx >= 0) {
        e.preventDefault();
        items[activeIdx].click();
      }
    }
  });

  document.addEventListener('click', (e) => {
    if (!$selector.contains(e.target)) closeDropdown();
  });

  // Global Escape to close dropdown
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape' && $dropdown.classList.contains('open')) closeDropdown();
  });
}

async function loadBranches() {
  try {
    const res = await fetch('/api/git/branches');
    if (!res.ok) return;
    const branches = await res.json();
    if (!Array.isArray(branches) || branches.length === 0) return;

    const current = branches.find(b => b.isCurrent);
    if (current) $name.textContent = current.name;

    $dropdown.innerHTML = '';
    for (const b of branches) {
      const item = document.createElement('div');
      item.className = 'branch-dropdown-item' + (b.isCurrent ? ' active' : '');
      item.textContent = b.name;
      item.setAttribute('role', 'option');
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        checkoutBranch(b.name);
      });
      $dropdown.appendChild(item);
    }
  } catch (err) { console.error('Branch load failed:', err); }
}

function toggleDropdown() {
  const isOpen = $dropdown.classList.toggle('open');
  $selector.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
}

function closeDropdown() {
  $dropdown.classList.remove('open');
  $selector.setAttribute('aria-expanded', 'false');
}

async function checkoutBranch(name) {
  closeDropdown();
  $name.textContent = name + '…';

  try {
    const res = await fetch('/api/git/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: name,
    });

    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      alert('Checkout failed: ' + (data.error || res.status));
      loadBranches(); // refresh to show real state
      return;
    }

    const data = await res.json();
    $name.textContent = data.branch;

    // Refresh file tree and close open tabs (files may have changed)
    const { loadFileTree } = await import('./file-tree.js');
    loadFileTree();

  } catch (err) {
    console.error('Checkout error:', err);
    $name.textContent = name;
  }
}