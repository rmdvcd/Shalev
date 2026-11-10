const { test } = require('node:test');
const assert = require('node:assert/strict');
const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../dshalev/static/pos.js'), 'utf8');
const markup = `<div id="pos-app" hidden data-user="1" data-shift="7" data-sale-saved="0" data-completed-sale-key="" data-pos-key-reset="0">
<input id="pos-search"><div id="pos-results"></div><p id="pos-results-status"></p>
<form id="pos-sale-form"><input name="csrf" value="test"><input name="sale_key" value="server-key"><input name="pos_prices"><div id="pos-lines"></div><p id="pos-empty"></p><b id="pos-total"></b><p id="pos-error"></p>
<select name="customer_id"><option value="">Sin cliente</option><option value="2">Ana</option></select>
<select name="payment_method"><option>EFECTIVO</option><option>TRANSFERENCIA</option></select>
<button id="pos-submit" type="submit"></button><button id="pos-clear" type="button"></button></form></div><div id="pos-fallback"></div>`;
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const items = [
  { id: 1, name: '<b>Café</b>', category: 'Producto', stock: 5, unit: 'unidad', sale_price_cents: 1250 },
  { id: 2, name: 'Té', category: 'Producto', stock: 2, unit: 'unidad', sale_price_cents: 275 }
];
function setup(saved, saleSaved = false, keyReset = false) {
  const dom = new JSDOM(markup, { url: 'http://localhost/ventas', runScripts: 'outside-only' });
  const { window } = dom;
  if (saved) window.sessionStorage.setItem('dshalev-pos-1-7-test', saved);
  if (keyReset) window.document.getElementById('pos-app').dataset.posKeyReset = '1';
  if (saleSaved) {
    window.document.getElementById('pos-app').dataset.saleSaved = '1';
    window.document.getElementById('pos-app').dataset.completedSaleKey = JSON.parse(saved || '{}').saleKey || '';
  }
  window.fetch = async url => {
    const params = new URL(url, 'http://localhost').searchParams;
    const ids = params.get('ids');
    const query = (params.get('q') || '').toLowerCase();
    return { ok: true, json: async () => ({ items: items.filter(i => ids ? ids.split(',').includes(String(i.id)) : i.name.toLowerCase().includes(query)), has_more: false }) };
  };
  window.confirm = () => true;
  window.eval(script);
  return dom;
}

test('carrito conserva artículos al buscar, calcula y restaura borrador', async () => {
  const dom = setup(); const w = dom.window; const d = w.document;
  await wait(10);
  assert.equal(d.querySelector('#pos-app').hidden, false);
  assert.equal(d.querySelector('#pos-fallback').hidden, true);
  assert.equal(d.querySelector('#pos-results b'), null); // nombres no son HTML
  d.querySelector('.pos-result').click();
  assert.equal(d.querySelector('#pos-total').textContent, '12.50 CUP');
  const search = d.querySelector('#pos-search'); search.value = 'Té'; search.dispatchEvent(new w.Event('input'));
  await wait(220);
  d.querySelector('.pos-result').click();
  assert.equal(d.querySelectorAll('#pos-lines .pos-line').length, 2);
  const qty = d.querySelector('[name="quantity_1"]'); qty.value = '2'; qty.dispatchEvent(new w.Event('input'));
  assert.equal(d.querySelector('#pos-total').textContent, '27.75 CUP');
  assert.equal(d.querySelector('#pos-submit').disabled, false);
  d.querySelector('[name="payment_method"]').value = 'TRANSFERENCIA';
  d.querySelector('[name="payment_method"]').dispatchEvent(new w.Event('change'));
  const saved = w.sessionStorage.getItem('dshalev-pos-1-7-test');
  const restored = setup(saved); await wait(10);
  assert.equal(restored.window.document.querySelectorAll('#pos-lines .pos-line').length, 2);
  assert.equal(restored.window.document.querySelector('#pos-total').textContent, '27.75 CUP');
  assert.equal(restored.window.document.querySelector('[name="payment_method"]').value, 'TRANSFERENCIA');
  const done = setup(saved, true); await wait(10);
  assert.equal(done.window.document.querySelectorAll('#pos-lines .pos-line').length, 0);
  dom.window.close(); restored.window.close(); done.window.close();
});

test('cantidades inválidas bloquean el registro y vaciar requiere confirmación', async () => {
  const dom = setup(); const w = dom.window; const d = w.document;
  await wait(10); d.querySelector('.pos-result').click();
  const qty = d.querySelector('[name="quantity_1"]'); qty.value = '0'; qty.dispatchEvent(new w.Event('input'));
  assert.equal(d.querySelector('#pos-submit').disabled, true);
  w.confirm = () => false; d.querySelector('#pos-clear').click();
  assert.equal(d.querySelectorAll('.pos-line').length, 1);
  w.confirm = () => true; d.querySelector('#pos-clear').click();
  assert.equal(d.querySelectorAll('.pos-line').length, 0);
  dom.window.close();
});

test('un borrador recuperado actualiza el precio antes de registrar', async () => {
  const saved = JSON.stringify({saleKey: '11111111-1111-4111-8111-111111111111', items: [{id: 1, name: 'Café', price: 900, quantity: 2}], customer: '', payment: 'EFECTIVO'});
  const dom = setup(saved); const d = dom.window.document;
  await wait(10);
  assert.equal(d.querySelector('#pos-total').textContent, '25 CUP');
  assert.match(d.querySelector('#pos-error').textContent, /Cambió el precio/);
  assert.equal(d.querySelector('#pos-submit').disabled, false);
  dom.window.close();
});

test('un conflicto de clave rota la clave y descarta el borrador anterior', async () => {
  const saved = JSON.stringify({saleKey: '11111111-1111-4111-8111-111111111111', items: [{id: 1, name: 'Café', price: 1250, quantity: 2}], customer: '', payment: 'EFECTIVO'});
  const dom = setup(saved, false, true); const d = dom.window.document;
  await wait(10);
  assert.equal(d.querySelectorAll('#pos-lines .pos-line').length, 0);
  assert.equal(dom.window.sessionStorage.getItem('dshalev-pos-1-7-test'), null);
  assert.match(d.querySelector('[name="sale_key"]').value, /^[0-9a-f-]{36}$/i);
  assert.notEqual(d.querySelector('[name="sale_key"]').value, 'server-key');
  dom.window.close();
});
