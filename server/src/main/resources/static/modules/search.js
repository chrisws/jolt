const $searchInput    = document.getElementById('search-input');
const $searchPanel    = document.getElementById('search-panel');
const $searchRes      = document.getElementById('search-results');
const $suggestDD      = document.getElementById('suggest-dropdown');
const $semanticToggle = document.getElementById('semantic-toggle');
const $caseToggle     = document.getElementById('case-toggle');
const $wordToggle     = document.getElementById('word-toggle');

let searchTimer     = null;
let suggestItems    = [];   // flat list of {hit, el} for keyboard nav
let suggestActive   = -1;   // index of highlighted item (-1 = none)
let semanticMode    = false; // true → use /api/search/semantic
let caseSensitive   = false; // true → case-sensitive matching
let wholeWord       = false; // true → whole-word matching
let abortController = null;  // cancel in-flight searches

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

import { buildSearchUrl } from './url-utils.js';

async function doSearch(query) {
  if (!query || query.length < 2) return;

  // Cancel any in-flight search
  if (abortController) abortController.abort();
  abortController = new AbortController();

  // Show searching indicator
  $searchRes.innerHTML = '<div class="empty-state"><span>Searching…</span></div>';
  $searchPanel.classList.add('open');

  try {
    const url = buildSearchUrl(query, { semantic: semanticMode, caseSensitive, wholeWord });
    const res = await fetch(url, { signal: abortController.signal });

    // Graceful 503 fallback — embedding model not loaded
    if (semanticMode && res.status === 503) {
      const errBody = await res.json().catch(() => ({}));
      $searchRes.innerHTML = '<div class="empty-state"><span>Semantic search unavailable — ' + esc(errBody.hint || 'embedding model not loaded') + '</span></div>';
      setTimeout(() => { semanticMode = false; $semanticToggle.classList.remove('active'); doSearch(query); }, 1500);
      return;
    }

    const results = await res.json();
    $searchRes.innerHTML = '';

    for (const r of results) {
      const el = document.createElement('div');
      el.className = 'result-item';
      el.innerHTML = '<div class="file-path">' + esc(r.filePath) + ':' + r.lineStart + '</div><div class="match-line">' + esc(r.name || r.qualifiedName || '') + '</div>';
      el.addEventListener('click', () => { import('./tabs.js').then(m => m.openTab(r.filePath)); closeSearch(); });
      $searchRes.appendChild(el);
    }

    if (results.length === 0) $searchRes.innerHTML = '<div class="empty-state"><span>No results</span></div>';
  } catch (err) {
    if (err.name === 'AbortError') return; // cancelled, ignore
    $searchRes.innerHTML = '<div class="empty-state search-error"><span>Search failed: ' + esc(err.message) + '</span></div>';
  }
}

export function closeSearch() { $searchPanel.classList.remove('open'); }

async function doSuggest(query) {
  if (!query || query.length < 2) { closeSuggest(); return; }
  try {
    const res = await fetch('/api/search/suggest?q=' + encodeURIComponent(query) + '&limit=10');
    const results = await res.json();
    renderSuggest(results);
  } catch (err) { console.error('Suggest failed:', err); closeSuggest(); }
}

function renderSuggest(results) {
  $suggestDD.innerHTML = '';
  suggestItems = [];
  suggestActive = -1;

  if (!results || results.length === 0) { closeSuggest(); return; }

  const groups = {};
  for (const r of results) {
    const kind = r.kind || 'FILE';
    if (!groups[kind]) groups[kind] = [];
    groups[kind].push(r);
  }

  const order = ['CLASS', 'METHOD', 'FIELD', 'FILE'];
  for (const kind of order) {
    const items = groups[kind];
    if (!items || items.length === 0) continue;

    const header = document.createElement('div');
    header.className = 'suggest-group';
    header.textContent = kind.toLowerCase() + 's';
    $suggestDD.appendChild(header);

    for (const r of items) {
      const el = document.createElement('div');
      el.className = 'suggest-item';
      el.setAttribute('role', 'option');
      el.innerHTML = '<span class="suggest-kind ' + esc(kind) + '">' + esc(kind) + '</span>' +
        '<span class="suggest-name">' + esc(r.name || r.qualifiedName || '') + '</span>' +
        '<span class="suggest-path">' + esc(r.filePath || '') + '</span>';
      el.addEventListener('click', () => selectSuggest(r));
      $suggestDD.appendChild(el);
      suggestItems.push({ hit: r, el });
    }
  }

  $suggestDD.classList.add('open');
}

function selectSuggest(hit) {
  closeSuggest();
  $searchInput.value = '';
  import('./tabs.js').then(m => m.openTab(hit.filePath));
}

function closeSuggest() { $suggestDD.classList.remove('open'); suggestItems = []; suggestActive = -1; }

function moveSuggest(dir) {
  if (suggestItems.length === 0) return;
  suggestActive = (suggestActive + dir + suggestItems.length) % suggestItems.length;
  suggestItems.forEach((item, i) => item.el.classList.toggle('active', i === suggestActive));
  const activeEl = suggestItems[suggestActive].el;
  activeEl.scrollIntoView({ block: 'nearest' });
}

export function focusSearch() { $searchInput.focus(); $searchInput.select(); }

export function initSearch() {
  $semanticToggle.addEventListener('click', () => {
    semanticMode = !semanticMode;
    $semanticToggle.classList.toggle('active', semanticMode);
  });

  $caseToggle.addEventListener('click', () => {
    caseSensitive = !caseSensitive;
    $caseToggle.classList.toggle('active', caseSensitive);
  });

  $wordToggle.addEventListener('click', () => {
    wholeWord = !wholeWord;
    $wordToggle.classList.toggle('active', wholeWord);
  });

  $searchInput.addEventListener('input', () => {
    clearTimeout(searchTimer);
    const val = $searchInput.value;
    searchTimer = setTimeout(() => doSuggest(val), 250);
  });

  $searchInput.addEventListener('keydown', e => {
    if (e.key === 'ArrowDown') { e.preventDefault(); moveSuggest(1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); moveSuggest(-1); }
    else if (e.key === 'Enter') {
      e.preventDefault();
      if (suggestActive >= 0 && suggestItems[suggestActive]) { selectSuggest(suggestItems[suggestActive].hit); }
      else { closeSuggest(); doSearch($searchInput.value); }
    }
    else if (e.key === 'Escape') { closeSuggest(); $searchInput.blur(); }
  });

  document.addEventListener('click', e => {
    if (!e.target.closest('.search-box')) closeSuggest();
  });

  document.getElementById('search-close').addEventListener('click', () => { closeSearch(); closeSuggest(); });
}
