import test from 'node:test';
import assert from 'node:assert/strict';
import { Miniflare } from 'miniflare';
import { fileURLToPath } from 'node:url';

test('runtime Cloudflare: dos conexiones reales, API hibernable y retransmisión binaria', { timeout: 30000 }, async () => {
  const mf = new Miniflare({
    modules: true,
    ratelimits: { ADMISSION: { namespace_id: "1001", simple: { limit: 60, period: 60 } } },
    modulesRules: [{ type: "ESModule", include: ["**/*.js"] }],
    scriptPath: fileURLToPath(new URL('../src/worker.js', import.meta.url)),
    compatibilityDate: '2026-08-06',
    durableObjects: { ROOMS: { className: 'RelayRoom', useSQLite: true } },
  });
  try {
    const base = 'https://relay.example/v1/' + 'c'.repeat(64);
    // En producción Cloudflare añade esta cabecera. No depender de la IP de loopback
    // que Miniflare detecte en Windows/Linux (puede ser IPv4, IPv6 o estar ausente).
    const options = () => ({ headers: { Upgrade: 'websocket', 'CF-Connecting-IP': '192.0.2.1' } });
    const p = await mf.dispatchFetch(base + '/principal', options());
    assert.equal(p.status, 101, p.status === 101 ? undefined : await p.text());
    p.webSocket.accept();
    const received = [];
    p.webSocket.addEventListener('message', e => received.push(e.data));
    const s = await mf.dispatchFetch(base + '/secundaria', options());
    assert.equal(s.status, 101, s.status === 101 ? undefined : await s.text());
    const ready = new Promise(resolve => s.webSocket.addEventListener('message', e => { if (e.data === 'ready') resolve(); }));
    s.webSocket.accept();
    await ready;
    const data = new Promise(resolve => p.webSocket.addEventListener('message', e => { if (typeof e.data !== 'string') resolve(e.data); }));
    s.webSocket.send(new Uint8Array([9, 8, 7]).buffer);
    assert.deepEqual(new Uint8Array(await data), new Uint8Array([9, 8, 7]));
    assert.ok(received.includes('ready'));
    const duplicate = await mf.dispatchFetch(base + '/secundaria', options());
    assert.equal(duplicate.status, 409);
    p.webSocket.close(1000);
  } finally { await mf.dispose(); }
});
