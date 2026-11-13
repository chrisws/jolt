/* ── Sidebar: collapsible sections + git commits list ── */

import { authFetch } from './url-utils.js';

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
}

async function loadSidebarCommits($gitList) {
  try {
    const res = await authFetch('/api/git/commits?page=0&size=10');
    if (!res.ok) return;
    const data = await res.json();

    let html = '<div class="gc-commits-label">Recent Commits</div>';

    if (!data.commits || data.commits.length === 0) {
      html += '<div class="empty-hint">No commits yet</div>';
      $gitList.insertAdjacentHTML('beforeend', html);
      return;
    }

    for (const c of (data.commits || [])) {
      const hash = c.hash ? c.hash.substring(0, 8) : '';
      const msg = esc((c.message || c.subject || '').split('\n')[0]);
      html += '<div class="gc-commit-item" role="button" tabindex="0" data-hash="' + hash + '" title="' + msg + '">';
      html += '<span class="gc-hash">' + hash + '</span>';
      html += '<span class="gc-msg">' + msg + '</span>';
      html += '</div>';
    }

    if (data.total > 10) {
      html += '<button class="gc-more-btn" id="gc-load-more">Load more…</button>';
    }

    $gitList.insertAdjacentHTML('beforeend', html);

    // Wire up commit clicks
    $gitList.querySelectorAll('.gc-commit-item').forEach(el => {
      el.addEventListener('click', () => { location.hash = '#/git/commits/' + el.dataset.hash; });
    });

    // Load more button
    const moreBtn = document.getElementById('gc-load-more');
    if (moreBtn) {
      let page = 1;
      moreBtn.addEventListener('click', async () => {
        moreBtn.textContent = 'Loading…';
        try {
          const r = await authFetch('/api/git/commits?page=' + page + '&size=10');
          const d = await r.json();
          for (const c of (d.commits || [])) {
            const h = c.hash ? c.hash.substring(0, 8) : '';
            const m = esc((c.message || c.subject || '').split('\n')[0]);
            const div = document.createElement('div');
            div.className = 'gc-commit-item';
            div.setAttribute('role', 'button');
            div.setAttribute('tabindex', '0');
            div.dataset.hash = h;
            div.title = m;
            div.innerHTML = '<span class="gc-hash">' + h + '</span><span class="gc-msg">' + m + '</span>';
            div.addEventListener('click', () => { location.hash = '#/git/commits/' + h; });
            moreBtn.before(div);
          }
          page++;
          moreBtn.textContent = 'Load more…';
        } catch (_) { moreBtn.textContent = 'Load more…'; }
      });
    }
  } catch (_) {}
}

export function initSidebar() {
  // ── Accordion sidebar sections (one open at a time) ──
  const $sections = document.querySelectorAll('.sidebar, .sidebar-section');
  $sections.forEach(section => {
    const label = section.querySelector('.section-label');
    if (!label) return;
    label.addEventListener('click', (e) => {
      if (e.target.closest('button')) return;
      const wasOpen = !section.classList.contains('collapsed');
      $sections.forEach(s => { s.classList.add('collapsed'); s.querySelector('.section-label')?.classList.add('collapsed'); });
      if (!wasOpen) {
        section.classList.remove('collapsed');
        label.classList.remove('collapsed');
      }
    });
  });

  // Start with Project open, others collapsed
  $sections.forEach((s, i) => { if (i > 0) { s.classList.add('collapsed'); s.querySelector('.section-label')?.classList.add('collapsed'); } });

  // ── Git Commits in sidebar ──
  const $gitList = document.getElementById('git-changes-list');
  if ($gitList) {
    loadSidebarCommits($gitList);
  }
}