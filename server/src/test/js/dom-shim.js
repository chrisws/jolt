/**
 * Minimal DOM shim for running browser modules under Node --test.
 * Import this at the top of any test file that loads DOM-dependent modules.
 */

function createMockElement(tag = 'div') {
  const el = {
    tagName: tag.toUpperCase(),
    children: [],
    style: {},
    dataset: {},
    isConnected: true,
    innerHTML: '',
    textContent: '',
    value: '',
    scrollTop: 0,
    offsetTop: 0,

    classList: {
      _set: new Set(),
      add(...c) { c.forEach(x => this._set.add(x)); },
      remove(...c) { c.forEach(x => this._set.delete(x)); },
      contains(c) { return this._set.has(c); },
      toggle(c) { this._set.has(c) ? this._set.delete(c) : this._set.add(c); },
    },

    _attrs: {},
    setAttribute(k, v) { this._attrs[k] = String(v); },
    getAttribute(k) { return this._attrs[k] ?? null; },
    removeAttribute(k) { delete this._attrs[k]; },

    addEventListener() {},
    removeEventListener() {},
    dispatchEvent() { return true; },

    appendChild(child) { this.children.push(child); return child; },
    removeChild(child) { const i = this.children.indexOf(child); if (i >= 0) this.children.splice(i, 1); return child; },
    insertBefore(child, ref) { const i = this.children.indexOf(ref); if (i >= 0) this.children.splice(i, 0, child); else this.children.push(child); return child; },
    prepend(...children) { children.reverse().forEach(c => this.children.unshift(c)); },

    querySelector() { return createMockElement(); },
    querySelectorAll() { return []; },
    getElementsByTagName() { return []; },

    focus() {},
    blur() {},
    click() {},

    cloneNode() { return createMockElement(tag); },
  };
  return el;
}

const _elements = new Map();

globalThis.document = {
  createElement: (tag) => createMockElement(tag),
  createTextNode: (text) => ({ textContent: text }),
  getElementById: (id) => _elements.get(id) ?? null,
  querySelector: () => createMockElement(),
  querySelectorAll: () => [],
  addEventListener: () => {},
  removeEventListener: () => {},
  body: createMockElement('body'),
  documentElement: createMockElement('html'),
};

globalThis.window = globalThis;
globalThis.window.location = { hash: '', href: 'http://localhost/', search: '' };
globalThis.window.prompt = () => null;
globalThis.window.confirm = () => true;
globalThis.window.alert = () => {};

// Register a well-known element so getElementById lookups don't return null
for (const id of ['code-viewer', 'main-content', 'search-panel', 'settings-panel', 'inspector-panel', 'feedback-modal', 'qo-backdrop', 'branch-dropdown', 'branch-selector', 'suggest-dropdown']) {
  _elements.set(id, createMockElement());
}

export { createMockElement, _elements };
