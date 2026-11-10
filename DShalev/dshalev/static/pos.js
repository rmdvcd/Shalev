(() => {
  const app = document.getElementById('pos-app');
  const fallback = document.getElementById('pos-fallback');
  if (!app || !fallback) return;
  const form = document.getElementById('pos-sale-form');
  const search = document.getElementById('pos-search');
  const results = document.getElementById('pos-results');
  const resultsStatus = document.getElementById('pos-results-status');
  const lines = document.getElementById('pos-lines');
  const empty = document.getElementById('pos-empty');
  const total = document.getElementById('pos-total');
  const error = document.getElementById('pos-error');
  const submit = document.getElementById('pos-submit');
  const clear = document.getElementById('pos-clear');
  const key = `dshalev-pos-${app.dataset.user}-${app.dataset.shift}-${form.elements.csrf.value}`;
  const cart = new Map();
  let timer, searchVersion = 0, checkingCart = false;

  function formatMoney(cents) {
    const whole = Math.floor(cents / 100).toLocaleString('en-US').replaceAll(',', ' ');
    return whole + (cents % 100 ? '.' + String(cents % 100).padStart(2, '0') : '') + ' CUP';
  }
  function save() {
    try {
      sessionStorage.setItem(key, JSON.stringify({items: [...cart.values()], customer: form.elements.customer_id.value, payment: form.elements.payment_method.value}));
    } catch (_) { /* El carrito sigue disponible en esta página aunque el navegador bloquee almacenamiento. */ }
  }
  function restore() {
    try {
      if (app.dataset.saleSaved === '1') {
        sessionStorage.removeItem(key);
        // No borrar una venta nueva si se recarga la URL de confirmación anterior.
        const url = new URL(window.location.href); url.searchParams.delete('notice');
        window.history.replaceState(null, '', url.pathname + url.search + url.hash);
        return;
      }
      const saved = JSON.parse(sessionStorage.getItem(key) || '{}');
      for (const item of (Array.isArray(saved.items) ? saved.items : []).slice(0, 60)) {
        if (Number.isSafeInteger(item.id) && item.id > 0 && typeof item.name === 'string' &&
            Number.isSafeInteger(item.price) && item.price >= 0 && Number.isSafeInteger(item.quantity) && item.quantity >= 1 && item.quantity <= 100000) cart.set(item.id, item);
      }
      if ([...form.elements.customer_id.options].some(option => option.value === saved.customer)) form.elements.customer_id.value = saved.customer;
      if (['EFECTIVO', 'TRANSFERENCIA'].includes(saved.payment)) form.elements.payment_method.value = saved.payment;
    } catch (_) { /* Un borrador ilegible no debe impedir vender. */ }
  }
  function updateTotal() {
    let cents = 0;
    let valid = cart.size > 0 && !checkingCart;
    for (const item of cart.values()) {
      if (!Number.isSafeInteger(item.quantity) || item.quantity < 1 || item.quantity > 100000) valid = false;
      else cents += item.price * item.quantity;
    }
    if (!Number.isSafeInteger(cents) || cents > 100000000000000) valid = false;
    total.textContent = Number.isSafeInteger(cents) ? formatMoney(cents) : 'Importe fuera de límite';
    submit.disabled = !valid;
    clear.disabled = cart.size === 0;
    empty.hidden = cart.size !== 0;
  }
  function renderCart() {
    lines.replaceChildren();
    for (const item of cart.values()) {
      const row = document.createElement('div'); row.className = 'pos-line';
      const description = document.createElement('div'); description.className = 'pos-line-name';
      const title = document.createElement('strong'); title.textContent = item.name;
      const price = document.createElement('small'); price.textContent = `${formatMoney(item.price)} / unidad`;
      description.append(title, price);
      const quantity = document.createElement('input');
      quantity.className = 'cart-quantity'; quantity.type = 'number'; quantity.min = '1'; quantity.max = '100000'; quantity.step = '1'; quantity.required = true;
      quantity.name = `quantity_${item.id}`; quantity.value = item.quantity; quantity.setAttribute('aria-label', `Cantidad de ${item.name}`);
      quantity.addEventListener('input', () => {
        item.quantity = quantity.value === '' ? 0 : Number(quantity.value);
        updateTotal(); save();
      });
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'pos-remove';
      remove.textContent = '×'; remove.setAttribute('aria-label', `Quitar ${item.name} del carrito`);
      remove.addEventListener('click', () => { cart.delete(item.id); error.textContent = ''; renderCart(); save(); });
      row.append(description, quantity, remove); lines.append(row);
    }
    updateTotal();
  }
  function add(item) {
    const existing = cart.get(item.id);
    if (existing) {
      if (existing.quantity >= 100000) { error.textContent = 'La cantidad máxima por artículo es 100 000.'; return; }
      existing.quantity = Math.max(0, existing.quantity) + 1;
      existing.price = item.sale_price_cents;
      existing.name = item.name;
    } else {
      if (cart.size >= 60) { error.textContent = 'El máximo es 60 artículos por venta.'; return; }
      cart.set(item.id, {id: item.id, name: item.name, price: item.sale_price_cents, quantity: 1});
    }
    error.textContent = ''; renderCart(); save();
    resultsStatus.textContent = `${item.name} agregado al carrito.`;
  }
  async function findItems() {
    const version = ++searchVersion;
    resultsStatus.textContent = 'Buscando artículos…';
    try {
      const response = await fetch(`/api/v1/pos/catalogo?q=${encodeURIComponent(search.value)}`, {cache: 'no-store'});
      if (!response.ok) throw new Error();
      const data = await response.json();
      if (version !== searchVersion) return;
      results.replaceChildren();
      for (const item of data.items) {
        const button = document.createElement('button'); button.type = 'button'; button.className = 'pos-result';
        const info = document.createElement('span'); info.className = 'pos-result-info';
        const name = document.createElement('strong'); name.textContent = item.name;
        const detail = document.createElement('small'); detail.textContent = item.category === 'Servicio' ? 'Servicio' : `Existencia: ${item.stock} ${item.unit}`;
        info.append(name, detail);
        const right = document.createElement('span'); right.className = 'pos-result-price'; right.textContent = `${formatMoney(item.sale_price_cents)}  +`;
        button.append(info, right); button.setAttribute('aria-label', `Agregar ${item.name}, ${formatMoney(item.sale_price_cents)}`);
        button.addEventListener('click', () => add(item)); results.append(button);
      }
      resultsStatus.textContent = data.items.length ? (data.has_more ? 'Primeros 30 resultados. Escribe más para precisar la búsqueda.' : `${data.items.length} resultado${data.items.length === 1 ? '' : 's'}.`) : 'No se encontraron artículos disponibles.';
    } catch (_) {
      if (version !== searchVersion) return;
      results.replaceChildren(); resultsStatus.textContent = 'No se pudo cargar el catálogo. Revisa la conexión y vuelve a buscar.';
    }
  }
  search.addEventListener('input', () => { ++searchVersion; clearTimeout(timer); timer = setTimeout(findItems, 180); });
  document.addEventListener('keydown', event => { if (event.key === 'F2') { event.preventDefault(); search.focus(); search.select(); } });
  clear.addEventListener('click', () => { if (cart.size && window.confirm('¿Vaciar el carrito actual?')) { cart.clear(); error.textContent = ''; renderCart(); save(); search.focus(); } });
  form.elements.customer_id.addEventListener('change', save);
  form.elements.payment_method.addEventListener('change', save);
  form.addEventListener('submit', event => {
    updateTotal();
    if (submit.disabled || !form.reportValidity()) { event.preventDefault(); error.textContent = 'Revisa las cantidades y el total antes de registrar.'; return; }
    form.elements.pos_prices.value = JSON.stringify(Object.fromEntries([...cart.values()].map(item => [item.id, item.price])));
    save(); submit.disabled = true; submit.textContent = 'Registrando…';
  });
  async function refreshRestoredCart() {
    if (!cart.size) return;
    checkingCart = true; updateTotal();
    const ids = [...cart.keys()];
    error.textContent = 'Comprobando precios del carrito recuperado…';
    try {
      const response = await fetch(`/api/v1/pos/catalogo?ids=${ids.join(',')}`, {cache: 'no-store'});
      if (!response.ok) throw new Error();
      const data = await response.json();
      let changed = false;
      for (const current of data.items) {
        const item = cart.get(current.id);
        if (item && item.price !== current.sale_price_cents) { item.price = current.sale_price_cents; changed = true; }
        if (item) item.name = current.name;
      }
      const returned = new Set(data.items.map(item => item.id));
      const missing = ids.some(id => cart.has(id) && !returned.has(id));
      checkingCart = false;
      error.textContent = missing ? 'Hay artículos que ya no están disponibles. Quítalos del carrito antes de registrar.' : changed ? 'Cambió el precio de un artículo. Revisa el nuevo total antes de registrar.' : 'Carrito recuperado. Revisa las cantidades antes de registrar.';
      renderCart(); save();
    } catch (_) {
      error.textContent = 'No se pudieron actualizar los precios. Recarga la página para volver a comprobarlos.';
      updateTotal();
    }
  }
  restore(); renderCart();
  app.hidden = false; fallback.hidden = true;
  findItems(); refreshRestoredCart();
})();
