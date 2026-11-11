const $selector = document.getElementById('branch-selector');
const $name = document.getElementById('branch-name');
const $dropdown = document.getElementById('branch-dropdown');

export function initBranchSelector() {
  loadBranches();
  $selector.addEventListener('click', toggleDropdown);
  document.addEventListener('click', (e) => {
    if (!$selector.contains(e.target)) closeDropdown();
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
  $dropdown.classList.toggle('open');
}

function closeDropdown() {
  $dropdown.classList.remove('open');
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