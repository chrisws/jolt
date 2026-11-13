import { authFetch } from './url-utils.js';
 const $settingsPanel = document.getElementById('settings-panel');
const $settingsBtn   = document.getElementById('settings-btn');
const $settingsClose = document.getElementById('settings-close');
const $settingsSave  = document.getElementById('settings-save');
const $settingsMsg   = document.getElementById('settings-saved-msg');
const $modelSelect   = document.getElementById('set-model-select');
const $modelLoadBtn  = document.getElementById('model-load-btn');
const $modelStatus   = document.getElementById('model-status');

export function openSettings() { loadSettings(); loadModels(); $settingsPanel.removeAttribute('inert'); $settingsPanel.classList.add('open'); }
export function closeSettings() { $settingsPanel.classList.remove('open'); $settingsPanel.setAttribute('inert', ''); }

async function loadSettings() {
  try {
    const res = await authFetch('/api/settings');
    const s = await res.json();

    document.getElementById('set-fuzzy-edits').value   = s['search.fuzzy.maxEdits'] ?? 2;
    document.getElementById('set-suggest-limit').value  = s['search.suggest.limit'] ?? 10;
    document.getElementById('set-diff-maxlines').value  = s['git.diff.maxLines'] ?? 1000;
    document.getElementById('set-diff-view').value      = s['git.diff.defaultView'] ?? 'unified';
    document.getElementById('set-keybindings-mode').value = s['keybindings.mode'] ?? 'default';

    $settingsMsg.textContent = '';
  } catch (err) { console.error('Settings load failed:', err); }
}

async function saveSettings() {
  const payload = {
    'search.fuzzy.maxEdits': parseInt(document.getElementById('set-fuzzy-edits').value, 10) || 2,
    'search.suggest.limit':  parseInt(document.getElementById('set-suggest-limit').value, 10) || 10,
    'git.diff.maxLines':     parseInt(document.getElementById('set-diff-maxlines').value, 10) || 1000,
    'git.diff.defaultView':  document.getElementById('set-diff-view').value || 'unified',
    'keybindings.mode':      document.getElementById('set-keybindings-mode').value || 'default'
  };

  const originalText = $settingsSave.textContent;
  $settingsSave.textContent = 'Saving…';
  $settingsSave.disabled = true;

  try {
    const res = await authFetch('/api/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    $settingsMsg.textContent = 'Saved ✓';
    setTimeout(() => { $settingsMsg.textContent = ''; }, 2000);
  } catch (err) { console.error('Settings save failed:', err); $settingsMsg.textContent = 'Save failed'; }
  finally {
    $settingsSave.textContent = originalText;
    $settingsSave.disabled = false;
  }
}

 async function loadModels() {
  try {
    const res = await fetch('/api/models');
    const models = await res.json();

    // Preserve current selection if still available
    const current = $modelSelect.value;
    $modelSelect.innerHTML = '<option value="">— none —</option>';

    for (const m of models) {
      const opt = document.createElement('option');
      opt.value = m;
      opt.textContent = m;
      $modelSelect.appendChild(opt);
    }

    if (current && models.includes(current)) {
      $modelSelect.value = current;
    }

    $modelStatus.textContent = '';
  } catch (err) { console.error('Model list load failed:', err); }
}

async function loadModel() {
  const model = $modelSelect.value;
  if (!model) { $modelStatus.textContent = 'No model selected'; return; }

  $modelLoadBtn.disabled = true;
  $modelStatus.textContent = 'Loading…';

  try {
    const res = await fetch('/api/models/select', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: model
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const data = await res.json();

    if (data.reindexing) {
      $modelStatus.textContent = 'Loaded — re-indexing…';
    } else {
      $modelStatus.textContent = 'Loaded ✓';
    }

    setTimeout(() => { $modelStatus.textContent = ''; }, 5000);
  } catch (err) {
    console.error('Model load failed:', err);
    $modelStatus.textContent = 'Failed: ' + err.message;
  } finally {
    $modelLoadBtn.disabled = false;
  }
}

export function initSettings() {
  $settingsBtn.addEventListener('click', openSettings);
  $settingsClose.addEventListener('click', closeSettings);
  $settingsSave.addEventListener('click', saveSettings);
  $modelLoadBtn.addEventListener('click', loadModel);
}
