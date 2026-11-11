const $divider = document.getElementById('sidebar-divider');

export function initDivider() {
  if (!$divider) return;

  let startX = 0;
  let startWidth = 0;

  $divider.addEventListener('mousedown', (e) => {
    e.preventDefault();
    startX = e.clientX;
    startWidth = $divider.parentElement.getBoundingClientRect().width - 
                document.querySelector('.main').getBoundingClientRect().width - 5;
    $divider.classList.add('dragging');
    document.body.classList.add('resizing');

    function onMove(ev) {
      const dx = ev.clientX - startX;
      const newWidth = Math.max(150, Math.min(600, startWidth + dx));
      document.documentElement.style.setProperty('--sidebar-width', newWidth + 'px');
    }

    function onUp() {
      $divider.classList.remove('dragging');
      document.body.classList.remove('resizing');
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    }

    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });
}
