const $panel  = document.getElementById('inspector-panel');
const $header = document.getElementById('inspector-title');
const $body   = document.getElementById('inspector-body');

function esc(s) { const d = document.createElement('div'); d.textContent = s; return d.innerHTML; }

export function openInspector(title) {
  $header.textContent = title;
  $body.innerHTML = '<div class="loading">Loading…</div>';
  $panel.classList.add('open');
}

export function closeInspector() { $panel.classList.remove('open'); }

/* ── Go-to-Definition ─────────────────────────── */

export async function showDefinition(symbol) {
  openInspector('Definition: ' + symbol);
  try {
    const res = await fetch('/api/definition?symbol=' + encodeURIComponent(symbol));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    let html = '<div class="insp-section">';
    html += '<div class="insp-row"><span class="insp-label">Symbol</span><span class="insp-val">' + esc(d.symbol) + '</span></div>';
    html += '<div class="insp-row"><span class="insp-label">Kind</span><span class="insp-val">' + esc(d.kind) + '</span></div>';
    html += '<div class="insp-row"><span class="insp-label">File</span><a class="insp-link" data-path="' + esc(d.filePath) + '" data-line="' + d.lineStart + '">' + esc(d.filePath) + ':' + d.lineStart + '</a></div>';
    if (d.superClass) html += '<div class="insp-row"><span class="insp-label">Extends</span><span class="insp-val">' + esc(d.superClass) + '</span></div>';
    html += '</div>';

    if (d.members && d.members.length > 0) {
      html += '<div class="insp-section"><h4>Members</h4><div class="insp-member-list">';
      for (const m of d.members) {
        html += '<div class="insp-member" data-path="' + esc(d.filePath) + '" data-line="' + m.lineStart + '">';
        html += '<span class="insp-kind ' + esc(m.kind) + '">' + esc(m.kind) + '</span>';
        html += '<span class="insp-name">' + esc(m.name) + '</span>';
        if (m.signature) html += '<span class="insp-sig muted">' + esc(m.signature) + '</span>';
        if (m.type) html += '<span class="insp-sig muted">' + esc(m.type) + '</span>';
        html += '</div>';
      }
      html += '</div></div>';
    }

    $body.innerHTML = html;
    wireInspectorLinks();
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── Find References ──────────────────────────── */

export async function showReferences(symbol) {
  openInspector('References: ' + symbol);
  try {
    const res = await fetch('/api/references?symbol=' + encodeURIComponent(symbol));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const refs = await res.json();

    if (!refs || refs.length === 0) { $body.innerHTML = '<div class="empty-state"><span>No references found</span></div>'; return; }

    let html = '<div class="insp-section"><h4>' + refs.length + ' reference(s)</h4><div class="insp-member-list">';
    for (const r of refs) {
      html += '<div class="insp-member" data-path="' + esc(r.filePath) + '" data-line="' + r.line + '">';
      html += '<span class="insp-file">' + esc(r.filePath) + '</span>';
      html += '<span class="insp-line">:' + r.line + '</span>';
      html += '</div>';
    }
    html += '</div></div>';

    $body.innerHTML = html;
    wireInspectorLinks();
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── Class Hierarchy ──────────────────────────── */

export async function showHierarchy(cls) {
  openInspector('Hierarchy: ' + cls);
  try {
    const res = await fetch('/api/hierarchy?class=' + encodeURIComponent(cls));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    let html = '';

    if (d.superClasses && d.superClasses.length > 0) {
      html += '<div class="insp-section"><h4>Super Classes</h4><div class="insp-tree">';
      for (const sc of d.superClasses) {
        html += '<div class="insp-tree-item">' + esc(sc) + '</div>';
      }
      html += '</div></div>';
    }

    if (d.interfaces && d.interfaces.length > 0) {
      html += '<div class="insp-section"><h4>Interfaces</h4><div class="insp-tree">';
      for (const iface of d.interfaces) {
        html += '<div class="insp-tree-item">' + esc(iface) + '</div>';
      }
      html += '</div></div>';
    }

    if (d.subTypes && d.subTypes.length > 0) {
      html += '<div class="insp-section"><h4>Sub Types (' + d.subTypes.length + ')</h4><div class="insp-tree">';
      for (const st of d.subTypes) {
        html += '<div class="insp-tree-item">' + esc(st) + '</div>';
      }
      html += '</div></div>';
    }

    if (!html) html = '<div class="empty-state"><span>No hierarchy info</span></div>';
    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── Call Graph ───────────────────────────────── */

export async function showCallGraph(method, depth) {
  openInspector('Call Graph: ' + method);
  try {
    const qs = new URLSearchParams({ method });
    if (depth) qs.set('depth', String(depth));

    const res = await fetch('/api/call-graph?' + qs.toString());
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    if (!d.edges || d.edges.length === 0) { $body.innerHTML = '<div class="empty-state"><span>No call edges found</span></div>'; return; }

    let html = '<div class="insp-section"><h4>' + d.edgeCount + ' edge(s), depth ' + d.depth + '</h4><table class="insp-table"><thead><tr><th>Caller</th><th>Callee</th><th>Line</th></tr></thead><tbody>';
    for (const e of d.edges) {
      html += '<tr><td>' + esc(e.caller) + '</td><td>' + esc(e.callee) + (e.receiver ? ' <span class="muted">(' + esc(e.receiver) + ')</span>' : '') + '</td><td>' + e.line + '</td></tr>';
    }
    html += '</tbody></table></div>';

    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── Who Calls Me ─────────────────────────────── */

export async function showCallers(method) {
  openInspector('Callers of: ' + method);
  try {
    const res = await fetch('/api/call-graph/callers?method=' + encodeURIComponent(method));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    if (!d.callers || d.callers.length === 0) { $body.innerHTML = '<div class="empty-state"><span>No callers found</span></div>'; return; }

    let html = '<div class="insp-section"><h4>' + d.callers.length + ' caller(s)</h4><div class="insp-tree">';
    for (const c of d.callers) {
      html += '<div class="insp-tree-item">' + esc(c) + '</div>';
    }
    html += '</div></div>';

    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── JSP Parse ────────────────────────────────── */

export async function showJspParse(path) {
  openInspector('JSP: ' + path.split('/').pop());
  try {
    const res = await fetch('/api/jsp/parse?path=' + encodeURIComponent(path));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    let html = '';

    if (d.elements && d.elements.length > 0) {
      html += '<div class="insp-section"><h4>Elements (' + d.elements.length + ')</h4><table class="insp-table"><thead><tr><th>Type</th><th>Name</th><th>Line</th><th>Content</th></tr></thead><tbody>';
      for (const e of d.elements) {
        html += '<tr><td><span class="insp-kind JSP">' + esc(e.type) + '</span></td><td>' + esc(e.name || '') + '</td><td>' + e.line + '</td><td class="insp-sig">' + esc(e.content || '') + '</td></tr>';
      }
      html += '</tbody></table></div>';
    }

    if (d.includedFiles && d.includedFiles.length > 0) {
      html += '<div class="insp-section"><h4>Included Files</h4><div class="insp-tree">';
      for (const f of d.includedFiles) {
        html += '<div class="insp-tree-item">' + esc(f) + '</div>';
      }
      html += '</div></div>';
    }

    if (d.tagLibUris && d.tagLibUris.length > 0) {
      html += '<div class="insp-section"><h4>Tag Lib URIs</h4><div class="insp-tree">';
      for (const u of d.tagLibUris) {
        html += '<div class="insp-tree-item">' + esc(u) + '</div>';
      }
      html += '</div></div>';
    }

    if (!html) html = '<div class="empty-state"><span>No JSP elements found</span></div>';
    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── Spring Beans ─────────────────────────────── */

export async function showSpringBeans(path) {
  openInspector('Spring: ' + path.split('/').pop());
  try {
    const res = await fetch('/api/spring/beans?path=' + encodeURIComponent(path));
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    let html = '';

    if (d.beans && d.beans.length > 0) {
      html += '<div class="insp-section"><h4>Beans (' + d.beans.length + ')</h4><table class="insp-table"><thead><tr><th>ID</th><th>Class</th><th>Scope</th><th>Depends On</th></tr></thead><tbody>';
      for (const b of d.beans) {
        html += '<tr><td>' + esc(b.id) + '</td><td class="insp-sig">' + esc(b.class || '') + '</td><td>' + esc(b.scope || 'singleton') + '</td><td>' + esc(b.dependsOn || '') + '</td></tr>';
      }
      html += '</tbody></table></div>';
    }

    if (d.componentScanPaths && d.componentScanPaths.length > 0) {
      html += '<div class="insp-section"><h4>Component Scan</h4><div class="insp-tree">';
      for (const p of d.componentScanPaths) {
        html += '<div class="insp-tree-item">' + esc(p) + '</div>';
      }
      html += '</div></div>';
    }

    if (d.imports && d.imports.length > 0) {
      html += '<div class="insp-section"><h4>Imports</h4><div class="insp-tree">';
      for (const i of d.imports) {
        html += '<div class="insp-tree-item">' + esc(i) + '</div>';
      }
      html += '</div></div>';
    }

    if (!html) html = '<div class="empty-state"><span>No beans found</span></div>';
    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── J2EE: web.xml ────────────────────────────── */

export async function showJ2eeWebXml() {
  openInspector('J2EE: web.xml');
  try {
    const res = await fetch('/api/j2ee/webxml');
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    if (!d.found) { $body.innerHTML = '<div class="empty-state"><span>No web.xml found in project</span></div>'; return; }

    let html = '';

    if (d.servlets && d.servlets.length > 0) {
      html += '<div class="insp-section"><h4>Servlets (' + d.servlets.length + ')</h4><table class="insp-table"><thead><tr><th>Name</th><th>Class</th><th>URL Pattern</th></tr></thead><tbody>';
      for (const s of d.servlets) {
        html += '<tr><td>' + esc(s.name) + '</td><td class="insp-sig">' + esc(s.class) + '</td><td>' + esc(s.urlPattern || '') + '</td></tr>';
      }
      html += '</tbody></table></div>';
    }

    if (d.filters && d.filters.length > 0) {
      html += '<div class="insp-section"><h4>Filters (' + d.filters.length + ')</h4><table class="insp-table"><thead><tr><th>Name</th><th>Class</th><th>URL Patterns</th></tr></thead><tbody>';
      for (const f of d.filters) {
        html += '<tr><td>' + esc(f.name) + '</td><td class="insp-sig">' + esc(f.class) + '</td><td>' + esc((f.urlPatterns || []).join(', ')) + '</td></tr>';
      }
      html += '</tbody></table></div>';
    }

    if (!html) html = '<div class="empty-state"><span>No servlets or filters found</span></div>';
    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── J2EE: persistence.xml ────────────────────── */

export async function showJ2eePersistence() {
  openInspector('J2EE: Persistence');
  try {
    const res = await fetch('/api/j2ee/persistence');
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    if (!d.found) { $body.innerHTML = '<div class="empty-state"><span>No persistence.xml found in project</span></div>'; return; }

    let html = '';

    if (d.entities && d.entities.length > 0) {
      html += '<div class="insp-section"><h4>Entities (' + d.entities.length + ')</h4><table class="insp-table"><thead><tr><th>Entity</th><th>Table</th><th>Fields</th></tr></thead><tbody>';
      for (const e of d.entities) {
        html += '<tr><td class="insp-sig">' + esc(e.entity) + '</td><td>' + esc(e.table || '') + '</td><td>' + esc((e.fields || []).join(', ')) + '</td></tr>';
      }
      html += '</tbody></table></div>';
    }

    if (!html) html = '<div class="empty-state"><span>No entities found</span></div>';
    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── J2EE: Annotations ────────────────────────── */

export async function showJ2eeAnnotations() {
  openInspector('J2EE: Annotations');
  try {
    const res = await fetch('/api/j2ee/annotations');
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    let html = '';

    if (d.ejbs && d.ejbs.length > 0) {
      html += '<div class="insp-section"><h4>EJB Components (' + d.ejbs.length + ')</h4><table class="insp-table"><thead><tr><th>Class</th><th>Type</th><th>File</th></tr></thead><tbody>';
      for (const e of d.ejbs) {
        html += '<tr><td class="insp-sig">' + esc(e.class) + '</td><td><span class="insp-kind EJB">' + esc(e.type) + '</span></td><td>' + esc(e.file) + '</td></tr>';
      }
      html += '</tbody></table></div>';
    }

    if (d.wiring && d.wiring.length > 0) {
      html += '<div class="insp-section"><h4>DI Wiring (' + d.wiring.length + ')</h4><table class="insp-table"><thead><tr><th>From</th><th>Field</th><th>To</th></tr></thead><tbody>';
      for (const w of d.wiring) {
        html += '<tr><td class="insp-sig">' + esc(w.from) + '</td><td>' + esc(w.field) + '</td><td class="insp-sig">' + esc(w.to) + '</td></tr>';
      }
      html += '</tbody></table></div>';
    }

    if (!html) html = '<div class="empty-state"><span>No EJB or DI annotations found</span></div>';
    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── J2EE: Wiring Graph ───────────────────────── */

export async function showJ2eeWiringGraph() {
  openInspector('J2EE: Wiring Graph');
  try {
    const res = await fetch('/api/j2ee/wiring-graph');
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const d = await res.json();

    if (!d.edges || d.edges.length === 0) { $body.innerHTML = '<div class="empty-state"><span>No wiring edges found</span></div>'; return; }

    let html = '<div class="insp-section"><h4>' + d.nodeCount + ' nodes, ' + d.edgeCount + ' edges</h4><table class="insp-table"><thead><tr><th>From</th><th>To</th></tr></thead><tbody>';
    for (const e of d.edges) {
      html += '<tr><td class="insp-sig">' + esc(e.from) + '</td><td class="insp-sig">' + esc(e.to) + '</td></tr>';
    }
    html += '</tbody></table></div>';

    $body.innerHTML = html;
  } catch (err) { $body.innerHTML = '<div class="empty-state"><span>' + esc(err.message) + '</span></div>'; }
}

/* ── Shared: wire click-to-open-file links ────── */

function wireInspectorLinks() {
  $body.querySelectorAll('[data-path]').forEach(el => {
    el.addEventListener('click', () => {
      import('./tabs.js').then(m => m.openTab(el.dataset.path));
      closeInspector();
    });
  });
}

export function initInspector() {
  document.getElementById('inspector-close').addEventListener('click', closeInspector);
}
