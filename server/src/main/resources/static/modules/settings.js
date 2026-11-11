const $settingsPanel = document.getElementById('settings-panel');
const $settingsBtn   = document.getElementById('settings-btn');
const $settingsClose = document.getElementById('settings-close');
const $settingsSave  = document.getElementById('settings-save');
const $settingsMsg   = document.getElementById('settings-saved-msg');

export function openSettings() { loadSettings(); $settingsPanel.classList.add('open'); }
export function closeSettings() { $settingsPanel.classList.remove('open'); }

async function loadSettings() {
  try {
    const res = await fetch('/api/settings');
    const s = await res.json();

    document.getElementById('set-fuzzy-edits').value   = s['search.fuzzy.maxEdits'] ?? 2;
    document.getElementById('set-suggest-limit').value  = s['search.suggest.limit'] ?? 10;
    document.getElementById('set-diff-maxlines').value  = s['git.diff.maxLines'] ?? 1000;
    document.getElementById('set-diff-view').value      = s['git.diff.defaultView'] ?? 'unified';

    $settingsMsg.textContent = '';
  } catch (err) { console.error('Settings load failed:', err); }
}

async function saveSettings() {
  const payload = {
    'search.fuzzy.maxEdits': parseInt(document.getElementById('set-fuzzy-edits').value, 10) || 2,
    'search.suggest.limit':  parseInt(document.getElementById('set-suggest-limit').value, 10) || 10,
    'git.diff.maxLines':     parseInt(document.getElementById('set-diff-maxlines').value, 10) || 1000,
    'git.diff.defaultView':  document.getElementById('set-diff-view').value || 'unified'
  };

  try {
    const res = await fetch('/api/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    $settingsMsg.textContent = 'Saved ✓';
    setTimeout(() => { $settingsMsg.textContent = ''; }, 2000);
  } catch (err) { console.error('Settings save failed:', err); $settingsMsg.textContent = ''; }
}

export function initSettings() {
  $settingsBtn.addEventListener('click', openSettings);
  $settingsClose.addEventListener('click', closeSettings);
  $settingsSave.addEventListener('click', saveSettings);
}
