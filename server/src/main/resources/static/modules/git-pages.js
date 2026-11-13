import { renderInlineDiff } from './diff-utils.js';
import { authFetch } from './url-utils.js';

const $viewer = document.getElementById('code-viewer');

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

function fmtDate(isoStr) {
  if (!isoStr) return '—';
  try {
    const d = new Date(isoStr);
    if (isNaN(d.getTime())) return '—';
    return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  } catch (_) { return isoStr; }
}

function statCard(label, value) {
  return '<div class="stat-card"><div class="stat-value">' + esc(String(value)) + '</div><div class="stat-label">' + esc(label) + '</div></div>';
}

function renderBranchSelector() {
  return '<select id="branch-select" class="branch-select" title="Current branch"><option>Loading…</option></select>';
}

async function loadBranches() {
  const $sel = document.getElementById('branch-select');
  if (!$sel) return;
  try {
    const res = await fetch('/api/git/branches');
    const branches = await res.json();
    $sel.innerHTML = '';
    for (const b of branches) {
      const opt = document.createElement('option');
      opt.value = b.name;
      opt.textContent = (b.isCurrent ? '✓ ' : '') + b.name;
      if (b.isCurrent) opt.selected = true;
      $sel.appendChild(opt);
    }
  } catch (_) {}
}

// Auto-load branches when git page renders
const _origRenderStats = renderGitStats;
renderGitStats = async function() { await _origRenderStats(); loadBranches(); };

/* ── Git Stats Dashboard ─────────────────────────── */

export async function renderGitStats() {
  $viewer.innerHTML = '<div class="loading">Loading stats…</div>';

  try {
    const res = await fetch('/api/git/stats');
    const s = await res.json();

    let html = '<div class="git-page">';
    html += '<div class="git-header"><h1>Repository Stats</h1>' + renderBranchSelector() + '<a href="#/git/commits" class="git-nav-link">View Commits &rarr;</a></div>';

    html += '<div class="stats-grid">';
    html += statCard('Total Commits', s.totalCommits || 0);
    html += statCard('Contributors', s.totalContributors || 0);
    html += statCard('First Commit', fmtDate(s.firstCommitDate));
    html += statCard('Last Commit', fmtDate(s.lastCommitDate));
    html += statCard('Current Branch', s.currentBranch || '—');
    html += statCard('Longest Streak', (s.longestStreak || 0) + ' days');
    html += '</div>';

    if (s.topCommitters && s.topCommitters.length > 0) {
      html += '<div class="section-block"><h2>Top Committers</h2><table class="data-table"><thead><tr><th>#</th><th>Name</th><th>Email</th><th>Commits</th></tr></thead><tbody>';
      s.topCommitters.forEach((c, i) => {
        const initials = c.name.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
        html += '<tr><td>' + (i + 1) + '</td><td><span class="avatar">' + esc(initials) + '</span> ' + esc(c.name) + '</td><td class="muted">' + esc(c.email) + '</td><td>' + c.commits + '</td></tr>';
      });
      html += '</tbody></table></div>';
    }

    if (s.busiestDay) {
      html += '<div class="flair-row"><span class="flair-badge">🔥 Busiest day: <strong>' + esc(s.busiestDay) + '</strong> (' + (s.busiestDayCount || 0) + ' commits)</span>';
      if (s.currentStreak > 0) html += ' <span class="flair-badge">📅 Current streak: <strong>' + s.currentStreak + ' days</strong></span>';
      html += '</div>';
    }

    html += '<div class="section-block"><h2>Activity Heatmap</h2><div id="heatmap-container"></div></div>';
    html += '</div>';
    $viewer.innerHTML = html;

    renderHeatmap(new Date().getFullYear());

  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed to load stats: ' + esc(err.message) + '</span></div>';
  }
}

/* ── Commit List with Pagination ─────────────────── */

export async function renderCommitList(params) {
  const page = parseInt(params[0]) || 0;
  const branch = params[1] || '';

  $viewer.innerHTML = '<div class="loading">Loading commits…</div>';

  try {
    const qs = new URLSearchParams({ page: page, size: 25 });
    if (branch) qs.set('branch', branch);

    const res = await fetch('/api/git/commits?' + qs.toString());
    const data = await res.json();

    let html = '<div class="git-page">';
    html += '<div class="git-header"><h1>Commits</h1><a href="#/git" class="git-nav-link">&larr; Stats</a></div>';

    html += '<div class="filter-bar">';
    html += '<input type="text" id="filter-author" placeholder="Filter by author…" value="">';
    html += '<input type="date" id="filter-from" title="From date">';
    html += '<input type="date" id="filter-to" title="To date">';
    html += '<button id="filter-apply" class="btn-small">Apply</button>';
    html += '</div>';

    html += '<div class="commit-list">';
    if (data.commits && data.commits.length > 0) {
      for (const c of data.commits) {
        const initials = c.author.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
        html += '<div class="commit-row" data-hash="' + esc(c.hash) + '">';
        html += '<span class="avatar">' + esc(initials) + '</span>';
        html += '<div class="commit-info">';
        html += '<div class="commit-msg"><a href="#/git/commits/' + esc(c.hash) + '">' + esc(c.message) + '</a></div>';
        html += '<div class="commit-meta"><span class="muted">' + esc(c.author) + '</span> · <span class="muted">' + fmtDate(c.date) + '</span> · <span class="hash-badge">' + esc(c.abbrevHash) + '</span> · ' + c.filesChanged + ' files</div>';
        html += '</div></div>';
      }
    } else {
      html += '<div class="empty-state"><span>No commits found</span></div>';
    }
    html += '</div>';

    if (data.totalPages > 1) {
      html += '<div class="pagination">';
      for (let p = 0; p < data.totalPages; p++) {
        if (p === page) html += '<span class="page-active">' + (p + 1) + '</span>';
        else html += '<a href="#/git/commits/' + p + '" class="page-link">' + (p + 1) + '</a>';
      }
      html += '</div>';
    }

    html += '</div>';
    $viewer.innerHTML = html;

    document.getElementById('filter-apply').addEventListener('click', () => {
      const author = document.getElementById('filter-author').value;
      const from = document.getElementById('filter-from').value;
      const to = document.getElementById('filter-to').value;

      const qs = new URLSearchParams({ page: 0, size: 25 });
      if (author) qs.set('author', author);
      if (from) qs.set('from', from + 'T00:00:00Z');
      if (to) qs.set('to', to + 'T23:59:59Z');

      fetch('/api/git/commits?' + qs.toString()).then(r => r.json()).then(d => {
        renderCommitListFiltered(d);
      });
    });

  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

function renderCommitListFiltered(data) {
  let html = '<div class="git-page">';
  html += '<div class="git-header"><h1>Commits (filtered)</h1><a href="#/git" class="git-nav-link">&larr; Stats</a></div>';
  html += '<div class="commit-list">';

  if (data.commits && data.commits.length > 0) {
    for (const c of data.commits) {
      const initials = c.author.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
      html += '<div class="commit-row" data-hash="' + esc(c.hash) + '">';
      html += '<span class="avatar">' + esc(initials) + '</span>';
      html += '<div class="commit-info">';
      html += '<div class="commit-msg"><a href="#/git/commits/' + esc(c.hash) + '">' + esc(c.message) + '</a></div>';
      html += '<div class="commit-meta"><span class="muted">' + esc(c.author) + '</span> · <span class="muted">' + fmtDate(c.date) + '</span> · <span class="hash-badge">' + esc(c.abbrevHash) + '</span> · ' + c.filesChanged + ' files</div>';
      html += '</div></div>';
    }
  } else {
    html += '<div class="empty-state"><span>No commits match filters</span></div>';
  }

  html += '</div></div>';
  $viewer.innerHTML = html;
}

/* ── Commit Detail with Diff ─────────────────────── */

export async function renderCommitDetail(hash) {
  $viewer.innerHTML = '<div class="loading">Loading commit…</div>';

  try {
    const res = await fetch('/api/git/commit/enhanced?hash=' + encodeURIComponent(hash));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    let html = '<div class="git-page">';

    html += '<div class="git-header"><h1>Commit</h1><a href="#/git/commits" class="git-nav-link">&larr; All Commits</a></div>';
    html += '<div class="commit-detail-header">';
    html += '<div class="cd-row"><span class="hash-badge full">' + esc(d.hash) + '</span></div>';
    html += '<div class="cd-row"><strong>' + esc(d.author) + '</strong> <span class="muted">&lt;' + esc(d.email) + '&gt;</span> · ' + fmtDate(d.date) + '</div>';
    html += '<div class="cd-msg">' + esc(d.message) + '</div>';
    if (d.parents && d.parents.length > 0) {
      html += '<div class="cd-row muted">Parent(s): ' + d.parents.map(p => '<span class="hash-badge">' + p.substring(0, 8) + '</span>').join(', ') + '</div>';
    }
    html += '</div>';

    html += '<div class="diff-view-toggle">';
    html += '<button data-view="unified" class="view-btn active">Unified</button>';
    html += '<button data-view="split" class="view-btn">Side-by-side</button>';
    html += '<button data-view="inline" class="view-btn">Inline</button>';
    html += '<button data-view="raw" class="view-btn">Raw</button>';
    html += '<a href="/api/git/commit/patch?hash=' + esc(hash) + '" class="btn-small" download>⬇ .patch</a>';
    html += '</div>';

    const fileStats = d.fileStats || [];
    html += '<div class="commit-detail-body">';
    html += '<div class="file-list-sidebar">';

    for (let i = 0; i < fileStats.length; i++) {
      const f = fileStats[i];
      const statusClass = f.status === 'A' ? 'st-add' : f.status === 'D' ? 'st-del' : 'st-mod';
      const statusLabel = f.status === 'A' ? '+' : f.status === 'D' ? '−' : '~';
      html += '<div class="file-list-item" data-idx="' + i + '" data-path="' + esc(f.path) + '">';
      html += '<span class="file-status ' + statusClass + '">' + statusLabel + '</span>';
      html += '<span class="file-name">' + esc(f.path.split('/').pop()) + '</span>';
      html += '<span class="file-stats">+' + f.linesAdded + ' −' + f.linesRemoved + '</span>';
      if (f.isBinary) html += '<span class="binary-badge">BIN</span>';
      html += '</div>';
    }

    html += '</div>';
    html += '<div class="diff-content" id="diff-content"><div class="loading">Select a file to view diff…</div></div>';
    html += '</div></div>';
    $viewer.innerHTML = html;

    window._commitData = d;

    document.querySelectorAll('.file-list-item').forEach(el => {
      el.addEventListener('click', () => loadFileDiff(hash, el.dataset.path, el.dataset.idx));
    });

    document.querySelectorAll('.view-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.view-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        window._diffView = btn.dataset.view;
        const activeFile = document.querySelector('.file-list-item.active');
        if (activeFile) loadFileDiff(hash, activeFile.dataset.path, activeFile.dataset.idx);
      });
    });

  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

async function loadFileDiff(commitHash, filePath, idx) {
  const diffArea = document.getElementById('diff-content');
  if (!diffArea) return;

  document.querySelectorAll('.file-list-item').forEach(el => el.classList.remove('active'));
  const activeEl = document.querySelector('.file-list-item[data-idx="' + idx + '"]');
  if (activeEl) activeEl.classList.add('active');

  const view = window._diffView || 'unified';

  const fileStats = (window._commitData && window._commitData.fileStats) || [];
  const fstat = fileStats.find(f => f.path === filePath);
  if (fstat && fstat.isBinary) {
    diffArea.innerHTML = '<div class="binary-diff"><span class="binary-badge">Binary files differ</span><br><a href="/api/git/commit/raw?hash=' + encodeURIComponent(commitHash) + '&path=' + encodeURIComponent(filePath) + '" target="_blank" class="git-nav-link">View full size &rarr;</a></div>';
    return;
  }

  diffArea.innerHTML = '<div class="loading">Loading diff…</div>';

  try {
    if (view === 'raw') {
      const res = await fetch('/api/git/commit/raw?hash=' + encodeURIComponent(commitHash) + '&path=' + encodeURIComponent(filePath));
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const text = await res.text();
      renderRawView(diffArea, filePath, text);
    } else {
      const res = await fetch('/api/git/file-diff?to=' + encodeURIComponent(commitHash) + '&path=' + encodeURIComponent(filePath));
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();

      const maxLines = 1000;
      const diffLines = data.diff ? data.diff.split('\n') : [];

      if (view === 'split') { renderSplitDiff(diffArea, filePath, diffLines); }
      else if (view === 'inline') { renderInlineDiff(diffArea, filePath, diffLines); }
      else { renderUnifiedDiff(diffArea, filePath, diffLines, maxLines); }
    }
  } catch (err) {
    diffArea.innerHTML = '<div class="empty-state"><span>Failed: ' + esc(err.message) + '</span></div>';
  }
}

/* ── Diff Rendering ──────────────────────────────── */

function renderUnifiedDiff(container, path, lines, maxLines) {
  let html = '<div class="diff-file-header">' + esc(path) + '</div>';
  html += '<div class="diff-body">';

  let shown = 0; let truncated = false;
  for (const line of lines) {
    if (shown >= maxLines) { truncated = true; break; }

    let cls = 'diff-ctx'; let prefix = ' ';
    if (line.startsWith('+')) { cls = 'diff-add'; prefix = '+'; }
    else if (line.startsWith('-')) { cls = 'diff-del'; prefix = '-'; }
    else if (line.startsWith('@@')) { cls = 'diff-hunk'; prefix = '@@'; }
    else if (line.startsWith('---') || line.startsWith('+++')) { cls = 'diff-meta'; prefix = line[0]; }

    const content = line.substring(1);
    html += '<div class="diff-line ' + cls + '"><span class="diff-prefix">' + esc(prefix) + '</span><span class="diff-text">' + esc(content) + '</span></div>';
    shown++;
  }

  if (truncated) {
    html += '<div class="diff-truncated">… ' + (lines.length - maxLines) + ' more lines … <a href="/api/git/commit/raw?hash=' + encodeURIComponent(window._commitData.hash) + '&path=' + encodeURIComponent(path) + '" target="_blank">View full file</a></div>';
  }

  html += '</div>';
  container.innerHTML = html;
}

function renderSplitDiff(container, path, lines) {
  let oldLines = [], newLines = [];

  for (const line of lines) {
    if (line.startsWith('+') && !line.startsWith('+++')) newLines.push(line.substring(1));
    else if (line.startsWith('-') && !line.startsWith('---')) oldLines.push(line.substring(1));
    else if (!line.startsWith('@@') && !line.startsWith('---') && !line.startsWith('+++')) {
      oldLines.push(line.substring(1));
      newLines.push(line.substring(1));
    }
  }

  const maxLen = Math.max(oldLines.length, newLines.length);
  while (oldLines.length < maxLen) oldLines.push('');
  while (newLines.length < maxLen) newLines.push('');

  let html = '<div class="diff-file-header">' + esc(path) + '</div>';
  html += '<div class="split-diff">';

  for (let i = 0; i < maxLen; i++) {
    const o = oldLines[i], n = newLines[i];
    const oCls = o === n ? 'split-ctx' : 'split-del';
    const nCls = o === n ? 'split-ctx' : 'split-add';

    html += '<div class="split-row">';
    html += '<div class="split-old"><span class="split-ln">' + (i + 1) + '</span><span class="' + oCls + '">' + esc(o) + '</span></div>';
    html += '<div class="split-new"><span class="split-ln">' + (i + 1) + '</span><span class="' + nCls + '">' + esc(n) + '</span></div>';
    html += '</div>';
  }

  html += '</div>';
  container.innerHTML = html;
}

function renderRawView(container, path, text) {
  const lines = text.split('\n');
  let html = '<div class="diff-file-header">' + esc(path) + ' <span class="muted">(' + lines.length + ' lines)</span></div>';
  html += '<div class="raw-view">';

  for (let i = 0; i < lines.length; i++) {
    html += '<div class="raw-line"><span class="line-num">' + (i + 1) + '</span><span>' + esc(lines[i]) + '</span></div>';
  }

  html += '</div>';
  container.innerHTML = html;
}

/* ── Activity Heatmap ────────────────────────────── */

export async function renderHeatmap(year) {
  const container = document.getElementById('heatmap-container');
  if (!container) return;

  try {
    const res = await fetch('/api/git/activity?year=' + year);
    const data = await res.json();
    const cells = data.cells || [];

    if (cells.length === 0) { container.innerHTML = '<div class="muted">No activity data</div>'; return; }

    const maxCount = Math.max(...cells.map(c => c.count), 1);

    let html = '<div class="heatmap">';

    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    let monthLabels = '';
    let lastMonth = -1;
    for (const cell of cells) {
      const m = parseInt(cell.date.substring(5, 7)) - 1;
      if (m !== lastMonth) { monthLabels += '<span class="hm-month">' + months[m] + '</span>'; lastMonth = m; }
      else { monthLabels += '<span class="hm-month-sp"></span>'; }
    }

    for (let i = 0; i < cells.length; i += 7) {
      html += '<div class="hm-week">';
      for (let d = 0; d < 7 && (i + d) < cells.length; d++) {
        const cell = cells[i + d];
        const ratio = cell.count / maxCount;
        let colorClass = 'hm-0';
        if (cell.count > 0) {
          if (ratio <= 0.25) colorClass = 'hm-1';
          else if (ratio <= 0.5) colorClass = 'hm-2';
          else if (ratio <= 0.75) colorClass = 'hm-3';
          else colorClass = 'hm-4';
        }

        html += '<div class="hm-cell ' + colorClass + '" title="' + cell.count + ' commits on ' + cell.date + '" data-date="' + cell.date + '"></div>';
      }
      html += '</div>';
    }

    html += '</div>';

    html += '<div class="hm-legend"><span>Less</span><span class="hm-cell hm-0"></span><span class="hm-cell hm-1"></span><span class="hm-cell hm-2"></span><span class="hm-cell hm-3"></span><span class="hm-cell hm-4"></span><span>More</span></div>';

    container.innerHTML = html;

    container.querySelectorAll('.hm-cell[data-date]').forEach(el => {
      el.addEventListener('click', () => {
        const date = el.dataset.date;
        location.hash = '#/git/commits/0';
        setTimeout(() => {
          fetch('/api/git/commits?page=0&size=25&from=' + date + 'T00:00:00Z&to=' + date + 'T23:59:59Z')
            .then(r => r.json())
            .then(d => renderCommitListFiltered(d));
        }, 100);
      });
    });

  } catch (err) { console.error('Heatmap failed:', err); }
}

/* ── Git Changes (Working Tree) ──────────────────── */

export async function renderGitChanges() {
  $viewer.innerHTML = '<div class="loading">Loading working tree…</div>';

  try {
    const res = await fetch('/api/git/status?untracked=true');
    const files = await res.json();

    const remotesRes = await fetch('/api/git/remotes');
    const remotes = await remotesRes.json();

    let html = '<div class="git-page">';
    html += '<div class="git-header"><h1>Changes</h1><a href="#/git" class="git-nav-link">&larr; Stats</a></div>';

    // ── Working Tree Status ──
    html += '<div class="section-block"><h2>Working Tree</h2>';

    if (files.length === 0) {
      html += '<div class="empty-state"><span>✓ Working tree is clean</span></div>';
    } else {
      // Group by type
      const modified = files.filter(f => f.type === 'modified');
      const deleted = files.filter(f => f.type === 'deleted');
      const untracked = files.filter(f => f.type === 'untracked');

      if (modified.length > 0) {
        html += '<div class="wt-group"><div class="wt-group-label">Modified</div>';
        for (const f of modified) {
          html += wtFileRow(f.path, 'modified');
        }
        html += '</div>';
      }

      if (deleted.length > 0) {
        html += '<div class="wt-group"><div class="wt-group-label">Deleted</div>';
        for (const f of deleted) {
          html += wtFileRow(f.path, 'deleted');
        }
        html += '</div>';
      }

      if (untracked.length > 0) {
        html += '<div class="wt-group"><div class="wt-group-label">Untracked</div>';
        for (const f of untracked) {
          html += wtFileRow(f.path, 'untracked');
        }
        html += '</div>';
      }

      // Stage All button
      html += '<div class="wt-actions"><button id="btn-stage-all" class="btn-small">Stage All</button></div>';
    }

    html += '</div>';

    // ── Commit Section ──
    html += '<div class="section-block"><h2>Commit</h2>';
    html += '<textarea id="commit-msg" rows="3" placeholder="Commit message…"></textarea>';
    html += '<div class="commit-author-row">';
    html += '<input type="text" id="commit-author-name" placeholder="Author name (optional)">';
    html += '<input type="text" id="commit-author-email" placeholder="Author email (optional)">';
    html += '</div>';
    html += '<button id="btn-commit" class="btn-primary">Commit</button>';
    html += '</div>';

    // ── Push / Pull Section ──
    if (remotes.length > 0) {
      html += '<div class="section-block"><h2>Push / Pull</h2>';
      html += '<div class="push-pull-row">';

      let remoteOpts = '';
      for (const r of remotes) {
        remoteOpts += '<option value="' + esc(r) + '">' + esc(r) + '</option>';
      }

      html += '<select id="push-pull-remote">' + remoteOpts + '</select>';
      html += '<input type="text" id="push-pull-branch" placeholder="Branch (default: current)">';

      html += '<button id="btn-push" class="btn-small">Push</button>';
      html += '<button id="btn-pull" class="btn-small">Pull</button>';
      html += '</div></div>';
    }

    html += '</div>';
    $viewer.innerHTML = html;

    // ── Event Listeners ──

    // Stage / Unstage per file
    document.querySelectorAll('.wt-stage-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const path = btn.dataset.path;
        const isStaged = btn.dataset.staged === 'true';

        if (isStaged) {
          // Unstage
          const res = await authFetch('/api/git/unstage?path=' + encodeURIComponent(path), { method: 'POST' });
          if (res.ok) { btn.textContent = 'Stage'; btn.dataset.staged = 'false'; }
        } else {
          // Stage
          const res = await authFetch('/api/git/add?path=' + encodeURIComponent(path), { method: 'POST' });
          if (res.ok) { btn.textContent = 'Unstage'; btn.dataset.staged = 'true'; }
        }
      });
    });

    // Stage All
    const $stageAll = document.getElementById('btn-stage-all');
    if ($stageAll) {
      $stageAll.addEventListener('click', async () => {
        const res = await authFetch('/api/git/stage-all', { method: 'POST' });
        if (res.ok) renderGitChanges(); // refresh to show updated state
      });
    }

    // Commit
    const $commitBtn = document.getElementById('btn-commit');
    if ($commitBtn) {
      $commitBtn.addEventListener('click', async () => {
        const message = document.getElementById('commit-msg').value.trim();
        if (!message) { alert('Please enter a commit message.'); return; }

        const authorName = document.getElementById('commit-author-name').value.trim() || null;
        const authorEmail = document.getElementById('commit-author-email').value.trim() || null;

        $commitBtn.disabled = true;
        $commitBtn.textContent = 'Committing…';

        try {
          const res = await authFetch('/api/git/commit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message, authorName, authorEmail }),
          });

          if (res.ok) {
            const data = await res.json();
            alert('Committed: ' + data.hash.substring(0, 8));
            renderGitChanges(); // refresh
          } else {
            const data = await res.json().catch(() => ({}));
            alert('Commit failed: ' + (data.error || res.status));
            $commitBtn.disabled = false;
            $commitBtn.textContent = 'Commit';
          }
        } catch (err) {
          alert('Commit failed: ' + err.message);
          $commitBtn.disabled = false;
          $commitBtn.textContent = 'Commit';
        }
      });
    }

    // Push / Pull
    const $pushBtn = document.getElementById('btn-push');
    const $pullBtn = document.getElementById('btn-pull');

    if ($pushBtn) {
      $pushBtn.addEventListener('click', async () => {
        const remote = document.getElementById('push-pull-remote').value;
        const branch = document.getElementById('push-pull-branch').value.trim() || null;

        if (!confirm('Push to ' + remote + (branch ? '/' + branch : '') + '?')) return;

        $pushBtn.disabled = true;
        $pushBtn.textContent = 'Pushing…';

        try {
          const res = await authFetch('/api/git/push', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ remote, branch }),
          });

          const data = await res.json();
          alert(data.ok ? 'Push successful' : 'Push failed');
        } catch (err) {
          alert('Push failed: ' + err.message);
        }

        $pushBtn.disabled = false;
        $pushBtn.textContent = 'Push';
      });
    }

    if ($pullBtn) {
      $pullBtn.addEventListener('click', async () => {
        const remote = document.getElementById('push-pull-remote').value;
        const branch = document.getElementById('push-pull-branch').value.trim() || null;

        if (!confirm('Pull from ' + remote + (branch ? '/' + branch : '') + '?')) return;

        $pullBtn.disabled = true;
        $pullBtn.textContent = 'Pulling…';

        try {
          const res = await authFetch('/api/git/pull', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ remote, branch }),
          });

          const data = await res.json();
          alert(data.ok ? 'Pull successful' : 'Pull failed');
          renderGitChanges(); // refresh after pull
        } catch (err) {
          alert('Pull failed: ' + err.message);
        }

        $pullBtn.disabled = false;
        $pullBtn.textContent = 'Pull';
      });
    }

  } catch (err) {
    $viewer.innerHTML = '<div class="empty-state"><span>Failed to load changes: ' + esc(err.message) + '</span></div>';
  }
}

function wtFileRow(path, type) {
  const icon = type === 'modified' ? '~' : type === 'deleted' ? '−' : '+';
  const cls = type === 'modified' ? 'wt-mod' : type === 'deleted' ? 'wt-del' : 'wt-new';

  let html = '<div class="wt-file-row">';
  html += '<span class="wt-icon ' + cls + '">' + icon + '</span>';
  html += '<span class="wt-path">' + esc(path) + '</span>';
  html += '<button class="btn-small wt-stage-btn" data-path="' + esc(path) + '" data-staged="false">Stage</button>';
  html += '</div>';

  return html;
}
