(() => {
  const key = 'dshalev-tema';
  const valid = ['sistema', 'claro', 'oscuro'];
  let choice = 'sistema';
  try { const stored = localStorage.getItem(key); if (valid.includes(stored)) choice = stored; } catch (_) {}
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)');
  function apply() {
    document.documentElement.dataset.theme = choice === 'sistema' ? (prefersDark.matches ? 'oscuro' : 'claro') : choice;
  }
  apply();
  prefersDark.addEventListener('change', () => { if (choice === 'sistema') apply(); });
  document.addEventListener('DOMContentLoaded', () => {
    const select = document.getElementById('theme-select');
    if (!select) return;
    select.value = choice;
    select.addEventListener('change', () => {
      if (!valid.includes(select.value)) return;
      choice = select.value;
      try { localStorage.setItem(key, choice); } catch (_) {}
      apply();
    });
  });
})();
