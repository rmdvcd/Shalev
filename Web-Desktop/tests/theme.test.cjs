const { test } = require('node:test');
const assert = require('node:assert/strict');
const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../dshalev/static/theme.js'), 'utf8');

function create(choice, dark) {
  const dom = new JSDOM('<html data-theme="claro"><body><select id="theme-select"><option value="sistema">Sistema</option><option value="claro">Claro</option><option value="oscuro">Oscuro</option></select></body></html>', {url:'http://localhost/', runScripts:'outside-only'});
  const window = dom.window;
  const media = {matches: dark, addEventListener: (_type, fn) => { media.change = fn; }};
  window.matchMedia = () => media;
  if (choice) window.localStorage.setItem('dshalev-tema', choice);
  window.eval(script);
  window.document.dispatchEvent(new window.Event('DOMContentLoaded'));
  return {dom, window, media};
}

test('sistema sigue el modo del dispositivo y las opciones explícitas persisten', () => {
  const {dom, window, media} = create(null, true);
  const select = window.document.getElementById('theme-select');
  assert.equal(window.document.documentElement.dataset.theme, 'oscuro');
  select.value = 'claro'; select.dispatchEvent(new window.Event('change'));
  assert.equal(window.localStorage.getItem('dshalev-tema'), 'claro');
  assert.equal(window.document.documentElement.dataset.theme, 'claro');
  media.matches = false; media.change();
  assert.equal(window.document.documentElement.dataset.theme, 'claro');
  select.value = 'sistema'; select.dispatchEvent(new window.Event('change'));
  assert.equal(window.document.documentElement.dataset.theme, 'claro');
  media.matches = true; media.change();
  assert.equal(window.document.documentElement.dataset.theme, 'oscuro');
  dom.window.close();
});

test('se recupera la preferencia y se ignora un valor desconocido', () => {
  const first = create('oscuro', false);
  assert.equal(first.window.document.documentElement.dataset.theme, 'oscuro');
  first.dom.window.close();
  const second = create('invalido', false);
  assert.equal(second.window.document.documentElement.dataset.theme, 'claro');
  second.dom.window.close();
});
