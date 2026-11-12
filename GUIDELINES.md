# Jolt Guidelines

Living reference doc — update as items are addressed throughout development.

---

## UI Review Checklist

### 1. Accessibility / ARIA

- [ ] Section labels (`.section-label`): add `tabindex="0"`, `role="button"`, `aria-expanded`, `aria-controls`
- [ ] File tree: add `role="tree"` on container, `role="treeitem"` + `aria-expanded` on items, `tabindex` for focus
- [ ] Tabs: add `aria-selected`, roving `tabindex`, Left/Right arrow key navigation (WAI-ARIA APG tab pattern)
- [ ] Git commit items (`.gc-commit-item`): add `tabindex="0"`, `role="option"` or make them `<button>`/`<a>`
- [ ] Branch selector: convert to `<button>` with `aria-expanded`, keyboard open/close
- [ ] Panels (search, settings, inspector): add `aria-hidden="true"` + `inert` when closed
- [ ] Modals (feedback, quick-open): add `role="dialog"`, `aria-modal="true"`, focus trap
- [ ] Add skip-navigation link (`<a class="skip-link" href="#main-content">`)
- [ ] Code viewer: add `role="region"` + `aria-label="Code viewer"`
- [ ] Ensure all interactive elements are reachable by Tab and activatable with Enter/Space

### 2. Capitalisation & Wording Consistency

- [ ] Git section: visible "Git Changes" vs `aria-label="Git changes"` → align to "Git Changes"
- [ ] Recent section: visible "Recent" vs `aria-label="Recent files"` → align (pick one)
- [ ] Audit all `aria-label` values against visible text for consistency
- [ ] Check dynamic strings (toast messages, status text) for consistent case (sentence case vs title case)

### 3. Mobile Phone Access

- [ ] Add `@media (max-width: 768px)` breakpoint:
  - [ ] Hide sidebar behind a hamburger/toggle button
  - [ ] Stack or hide topbar items (branch selector, toggle buttons)
  - [ ] Full-width search input
  - [ ] Increase base font size to ≥14px
- [ ] Increase touch targets to ≥44×44px (section labels, tab close buttons, tree items)
- [ ] Hide or widen the resize divider on touch devices (`@media (pointer: coarse)`)
- [ ] Add `touch-action: manipulation` on interactive elements to prevent double-tap zoom
- [ ] Test at 375px, 414px, 768px widths

### 4. Keyboard Usage

- [ ] Section labels: Enter/Space to toggle open/close
- [ ] File tree: ArrowUp/Down to navigate, ArrowRight/Left to expand/collapse, Enter to open file, Home/End
- [ ] Tabs: Left/Right arrows to switch (roving tabindex), Ctrl+W or middle-click to close
- [ ] Focus management: move focus into panel on open, return to trigger on close
- [ ] Focus trap in modals (feedback, quick-open) — Tab cycles within dialog
- [ ] Escape closes topmost panel/modal
- [ ] Branch selector: Enter/Space to open, ArrowUp/Down + Enter to select, Escape to close
- [ ] Code viewer: ArrowUp/Down to scroll, Ctrl+Home/End for top/bottom

### 5. XSS / Output Sanitisation

- [ ] Git commit messages in `index.html` inline script: only `<` and `"` are escaped — add `&` → `&amp;` (do `&` first)
- [ ] Audit all `innerHTML` assignments for unescaped API data (file names, paths, user input)
- [ ] Prefer `textContent` over `innerHTML` where no markup is needed
- [ ] Consider a CSP header (`script-src 'self'`) to mitigate inline script risks

### 6. Error Handling / Silent Failures

- [ ] Replace bare `catch (_) {}` blocks with user-visible feedback (toast, status bar message)
- [ ] File tree load failure: show "Failed to load — click to retry" in the tree area
- [ ] Git API failures: show "Git data unavailable" in the Git Changes section
- [ ] Settings load failure: fall back to defaults + show a one-time notification
- [ ] Search API failure: show error state in results panel, not just empty

### 7. Loading States & Feedback

- [ ] File content load: show a spinner or skeleton in the code viewer while fetching
- [ ] Search results: show "Searching…" indicator before results appear
- [ ] Git commits: show loading state in sidebar section
- [ ] Settings save: brief "Saving…" → "Saved ✓" feedback (partially done)
- [ ] Index rebuild (semantic model load): progress indicator with percentage

### 8. URL / State Sync

- [ ] Reflect open tabs in URL (e.g. `#/file/path/to/File.java:42`)
- [ ] Reflect active search query in URL query params
- [ ] Reflect inspector state (which panel, which symbol)
- [ ] Support deep-linking: opening a URL with `#/file/…` should restore that tab
- [ ] Browser back/forward should work for tab navigation (pushState/popstate)
- [ ] Shareable links should restore full context (file + line + panel)

### 9. Theme Completeness

- [ ] Audit all CSS variables: ensure every `--var` used in components is defined in both `:root` (dark) and `[data-theme="light"]`
- [ ] Check `--text-muted` contrast in light mode (may need to be darker)
- [ ] Check scrollbar colours, selection highlight, accent-dim in light mode
- [ ] Test: toggle theme with every panel open, tabs open, search active

### 10. Colour Contrast (WCAG AA)

- [ ] `--text-muted (#808080)` on `--surface (#2b2d30)` ≈ 4.6:1 — passes for ≥14px, **fails for 11px small text**
  - [ ] Either increase small-text size to 14px bold, or darken muted to ≥#6e6e6e for 4.5:1 at 11px
- [ ] `--text-muted` on `--bg (#1e1f22)` — verify ratio
- [ ] Accent (`#4a9eff`) on surface — verify for small labels
- [ ] Run a contrast checker (e.g. WebAIM) on all text/background pairs in both themes

### 11. Browser Compatibility

- [ ] Test in Firefox: `AuxClickEvent` (middle-click close tab), `structuredClone`, CSS `inset`
- [ ] Test in Safari: ES module behaviour, `optional chaining`, `Array.at()`
- [ ] Avoid Chrome-only APIs or provide fallbacks
- [ ] Test `prefers-reduced-motion` in all browsers
- [ ] Verify `:focus-visible` works consistently (Safari historically had bugs)

### 12. Destructive Action Guardrails

- [ ] "Close all tabs" (⌂): add confirmation if >1 tab open, or offer undo toast
- [ ] Delete bookmark: offer undo (5s toast with "Undo" button)
- [ ] Clear search history: confirmation dialog
- [ ] Any future destructive ops: confirm or undo

### 13. Empty / Edge-Case States

- [ ] Zero git commits: show "No commits yet" message in Git Changes section
- [ ] Zero bookmarks: show "No bookmarks — Ctrl+D to add" hint
- [ ] Very large file (>50k lines): virtualise rendering or show "File too large, showing first N lines"
- [ ] Directory with 2000+ files: lazy-load / paginate tree children
- [ ] Unicode/emoji in file names: verify `esc()` handles them, no layout breakage
- [ ] Empty search results: show "No matches for …" not just blank
- [ ] Network offline: show banner "Connection lost — retrying…"

### 14. Semantic HTML & Heading Hierarchy

- [ ] Add `<h1>` for the app title (currently in an `<a class="logo">`)
- [ ] Settings panel: `<h3>` sections with no `<h1>`/`<h2>` before them — add proper hierarchy
- [ ] Main content area: ensure a heading exists for screen reader navigation
- [ ] Use `<section>` with `aria-label` for logical groupings where appropriate
- [ ] Verify landmark roles: `banner` (header), `navigation` (sidebar), `main`, `complementary` (asides)

### 15. Caching & Refresh Behaviour

- [ ] Cache file tree response in memory; only re-fetch on git change events
- [ ] Progressive rendering for large trees (show top-level dirs immediately, lazy-load deeper)
- [ ] Consider a service worker for static asset caching (CSS, JS, hljs)
- [ ] On refresh: restore previous tabs from `sessionStorage` or URL
- [ ] Debounce file-tree re-fetch on rapid git operations

### 16. Performance

*Goal: Jolt should feel instant. No spinner visible for <300 ms interactions. Large repos should not degrade UX.*

### Initial Load

- [ ] Measure and log TTI (Time to Interactive) — target < 2 s on local network
- [ ] Parallelise startup API calls (settings, file tree, git status) instead of sequential `await`
- [ ] Lazy-load heavy modules: `hljs` (syntax highlighting), `marked` (markdown), inspector panels — only import when first needed
- [ ] Defer non-critical JS: branch selector, nav-history, context-menu can load after first paint
- [ ] Consider code-splitting: separate chunk for Git pages, Inspector, Settings so the initial bundle is smaller
- [ ] Preload/critical CSS inline; defer `hljs/theme-github-dark.css` until first file opens

### File Tree & Navigation

- [ ] Virtualise the file tree for directories with >100 children (render only visible rows)
- [ ] Cache directory listings in a Map; invalidate on git change events only
- [ ] Debounce tree re-renders when multiple file changes arrive in quick succession
- [ ] Avoid full `$fileTree.innerHTML = ''` re-render — use incremental DOM updates (add/remove changed nodes)

### Search & Indexing

- [ ] Debounce search input (300 ms) to avoid firing a Lucene query per keystroke
- [ ] Show previous results while new query is in flight (don't blank the panel)
- [ ] Cap result rendering to first 200 items; "Load more" for the rest
- [ ] Cancel in-flight search `fetch` via `AbortController` when user types a new query
- [ ] Semantic search: show a distinct "Searching embeddings…" state; cap concurrent embedding requests

### File Content Rendering

- [ ] Virtualise the code viewer for files >500 visible lines — only render rows in viewport ± buffer
- [ ] Defer syntax highlighting: render plain text first, apply hljs in a `requestIdleCallback`
- [ ] For very large files (>1 MB): show first 2000 lines + "Load full file" button
- [ ] Cache file contents in memory (LRU, max 50 files) to avoid re-fetch on tab switch
- [ ] Use `documentFragment` for batch DOM inserts; avoid repeated `appendChild` in loops

### Git Operations

- [ ] Git log: paginate (already done) + cache in memory; don't re-fetch on every sidebar open
- [ ] Git diff: cap rendered lines (already configurable); virtualise split-view for large diffs
- [ ] Git status: poll on a timer (5 s) or use WebSocket push, not per-interaction fetch
- [ ] Avoid blocking the main thread on large repo status — offload to a Web Worker if JGit calls are slow

### Memory & Long-Session Health

- [ ] Monitor DOM node count; warn or virtualise if >10k nodes
- [ ] Clear cached file contents when tabs are closed (don't accumulate)
- [ ] Avoid memory leaks from event listeners on dynamically created elements (use `AbortController` or delegation)
- [ ] Test: leave app open for 8 h with 30 tabs — verify no progressive slowdown

### Rendering & Main Thread

- [ ] Avoid layout thrashing: batch reads (`offsetHeight`) and writes (`style.display`) separately
- [ ] Use `content-visibility: auto` on off-screen sidebar sections to skip rendering
- [ ] Throttle scroll handlers (code viewer, search results) with `requestAnimationFrame`
- [ ] Profile with Chrome DevTools Performance tab: no single frame >16 ms during interactions

### Network Waterfall

- [ ] Audit initial page load: no sequential `await` chains that could be `Promise.all`
- [ ] Use HTTP caching headers (`ETag`, `Cache-Control`) on static assets and API responses
- [ ] Batch related API calls where possible (e.g. file content + blame in one request)
- [ ] Consider a lightweight BFF endpoint that returns tree + git-status + settings in one payload

### Targets / Budgets

| Metric                        | Target                  |
|-------------------------------|-------------------------|
| Initial JS bundle (gzipped)   | < 150 KB                |
| TTI (local, warm cache)       | < 1.5 s                 |
| File open → first paint       | < 200 ms (< 1000 lines) |
| Search keystroke → results    | < 300 ms                |
| Tab switch (cached file)      | < 50 ms                 |
| DOM node count (steady state) | < 5 000                 |
| Memory after 8 h / 30 tabs    | < 500 MB                |

---

## Backend

*Separate focus area — each sub-section can be a standalone session.*

### B1. API Response Times

- [ ] File read endpoint: <50 ms for files <1 MB
- [ ] Search (Lucene): <200 ms for typical queries
- [ ] Git log/diff: <500 ms for repos <50k commits
- [ ] Symbol lookup (definition, references): <100 ms
- [ ] Consistent `X-Response-Time` header on all endpoints for monitoring
- [ ] Log any request >500 ms at WARN level

### B2. Indexing Performance

- [ ] Initial index of a 10k-file repo: <30 s
- [ ] Incremental update (single file change): <500 ms
- [ ] No full re-index on minor edits (debounce + diff-based detection)
- [ ] Index build progress exposed via API/WebSocket (not silent)
- [ ] Graceful handling of very large repos (>100k files) — streaming, not all-in-heap
- [ ] Index stats endpoint: `/api/index/stats` (doc count, size, last update)

### B3. Memory Management

- [ ] Lucene index memory bounded (document RAM-backed vs MMap trade-off)
- [ ] JDT AST objects GC'd after parse (not retained in symbol table)
- [ ] File content cache: LRU with max heap budget (e.g. 256 MB)
- [ ] No unbounded collections (symbol table, call graph) — cap or evict
- [ ] Heap target: <1 GB for a 50k-file repo
- [ ] Verify no memory leak over 8 h with active file watcher

### B4. Concurrency & Thread Safety

- [ ] Symbol table: concurrent reads during index writes (COW map or ReadWriteLock)
- [ ] Lucene: single-writer / multi-reader (verify no accidental multi-writer path)
- [ ] WebSocket broadcast: non-blocking, doesn't stall index writes
- [ ] File watcher events: batched, not one-thread-per-event
- [ ] Thread pool bounds: file-watcher, index-writer, API-handler all bounded with rejection policy

### B5. Graceful Degradation & Error Handling

- [ ] Corrupt Lucene index: detect on open, offer re-index, don't crash
- [ ] Git repo locked / missing: API returns structured error, UI shows message
- [ ] OOM risk on huge file: reject with 413 + clear message, not a stack trace
- [ ] Partial index (crash mid-build): recover or rebuild cleanly on next start
- [ ] No silent `catch (Exception e) {}` in service layers — log + propagate
- [ ] All error responses use uniform format: `{ "error": "...", "code": "...", "details": "..." }`

### B6. API Design Consistency

- [ ] Pagination on all list endpoints (consistent `page`/`size`/`total`)
- [ ] `ETag` / `Last-Modified` on file content endpoints for conditional requests
- [ ] Idempotent PUTs (settings)
- [ ] Rate-limit or debounce hammering endpoints (search, file-tree)
- [ ] Consistent HTTP status codes (404 not 500 for missing files, 400 not 500 for bad params)
- [ ] OpenAPI / Swagger doc generated from annotations (or hand-maintained)

### B7. Logging & Observability

- [ ] Structured JSON logs (verify Logback pattern)
- [ ] Request timing middleware: log >500 ms requests at WARN
- [ ] Slow Lucene query logging (>200 ms) with query text
- [ ] Health check endpoint: `/api/health` (liveness + readiness)
- [ ] Index stats: doc count, heap usage, last update timestamp
- [ ] No sensitive data in logs (file contents, user tokens, full paths with PII)

### B8. Startup Time

- [ ] JVM + framework init: <3 s to accept first request
- [ ] Lucene index open (existing): <2 s for 10k docs
- [ ] ONNX model load: async, non-blocking; API returns "model loading" status until ready
- [ ] Total: server usable (file tree + search) in <5 s on SSD
- [ ] Startup progress visible in CLI output (not just silent wait)

### B9. Security (Backend)

- [ ] Path traversal: `/api/file/tree?path=../../etc/passwd` must be rejected (resolve + verify prefix)
- [ ] Git command injection: never interpolate user input into shell/process commands
- [ ] File API: restrict to repo root, no `..` escape, no symlink follow
- [ ] WebSocket: validate origin, rate-limit messages
- [ ] No sensitive data in logs or error responses
- [ ] Reject requests with malformed headers / oversized bodies early

### B10. Resource Limits & Guardrails

- [ ] Max file size to serve/highlight: 5 MB (reject above with 413)
- [ ] Max repo size to index: configurable, warn at 200k files
- [ ] Max concurrent search queries: 4 (queue or reject with 429)
- [ ] Max file tree depth: 20 (prevent pathological nesting)
- [ ] Connection / thread pool limits documented and enforced
- [ ] Graceful shutdown: finish in-flight requests, close Lucene index, flush file watcher

---

## Status Legend

| Mark  | Meaning                      |
|-------|------------------------------|
| `[ ]` | Not started                  |
| `[x]` | Done                         |
| `[~]` | In progress / partially done |
| `[!]` | Blocked / needs discussion   |

*Last updated: 2026-11-13*
