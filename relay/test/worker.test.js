import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { RelayRoom } from '../src/worker.js';
import { route, MAX_CHUNK, MAX_SESSION } from '../src/protocol.js';
const url = 'https://relay.example/v1/' + 'a'.repeat(64);
const request = role => new Request(url + '/' + role, { headers: { Upgrade: 'websocket' } });
class Socket {
  sent = []; closed = false;
  serializeAttachment(v) { this.meta = structuredClone(v); }
  deserializeAttachment() { return structuredClone(this.meta); }
  send(v) { this.sent.push(v); }
  close() { this.closed = true; }
}
function room() {
  const sockets = [];
  const ctx = { getWebSockets: () => sockets.filter(s => !s.closed), acceptWebSocket: s => sockets.push(s) };
  return { relay: new RelayRoom(ctx), sockets };
}
// Response nativo de Node no permite 101; simulamos únicamente el upgrade del runtime Cloudflare.
globalThis.WebSocketPair = class { constructor() { this[0] = new Socket(); this[1] = new Socket(); } };
const NativeResponse = globalThis.Response;
globalThis.Response = class extends NativeResponse {
  constructor(body, init) { super(body, init?.status === 101 ? { status: 200 } : init); this.upgrade = init?.webSocket; }
};
test('ruta estricta, sin consultas, Origin ni roles arbitrarios', () => {
  assert.ok(route(request('principal')));
  for (const u of [url + '/otro', url + '/principal?token=x', 'https://relay.example/v1/x/principal'])
    assert.equal(route(new Request(u, { headers: { Upgrade: 'websocket' } })), null);
  assert.equal(route(new Request(url + '/principal', { headers: { Upgrade: 'websocket', Origin: 'https://evil.example' } })), null);
  assert.equal(route(new Request(url + '/principal')), null);
});
test('rechaza antes de crear sala ante petición inválida', async () => {
  assert.equal((await worker.fetch(new Request(url), {})).status, 400);
});
test('empareja ambos órdenes, no envía ready antes de tener pareja', async () => {
  for (const order of [['principal', 'secundaria'], ['secundaria', 'principal']]) {
    const { relay, sockets } = room();
    await relay.fetch(request(order[0])); assert.deepEqual(sockets[0].sent, []);
    await relay.fetch(request(order[1]));
    assert.deepEqual(sockets.map(s => s.sent), [['ready'], ['ready']]);
    const bytes = new Uint8Array([1, 2, 3]).buffer;
    relay.webSocketMessage(sockets[0], bytes);
    assert.equal(sockets[1].sent.at(-1), bytes);
    assert.equal((await relay.fetch(request(order[0]))).status, 409);
  }
});
test('rechaza datos sin pareja, texto y mensajes excesivos', async () => {
  for (const data of ['texto', new ArrayBuffer(MAX_CHUNK + 1), new ArrayBuffer(0)]) {
    const { relay, sockets } = room();
    await relay.fetch(request('principal')); await relay.fetch(request('secundaria'));
    relay.webSocketMessage(sockets[0], data); assert.ok(sockets.every(s => s.closed));
  }
  const { relay, sockets } = room(); await relay.fetch(request('principal'));
  relay.webSocketMessage(sockets[0], new ArrayBuffer(1)); assert.ok(sockets[0].closed);
});
test('cierre/error cierra ambos extremos y limita bytes acumulados', async () => {
  for (const action of ['webSocketClose', 'webSocketError', 'limite']) {
    const { relay, sockets } = room();
    await relay.fetch(request('principal')); await relay.fetch(request('secundaria'));
    if (action === 'limite') {
      sockets[0].meta.bytes = MAX_SESSION;
      relay.webSocketMessage(sockets[0], new ArrayBuffer(1));
    } else relay[action](sockets[0]);
    assert.ok(sockets.every(s => s.closed));
  }
});

test('limita admisión antes de crear objetos y rechaza IP ausente', async () => {
  assert.equal((await worker.fetch(request('principal'), {})).status, 400);
  const r = new Request(url + '/principal', { headers: { Upgrade: 'websocket', 'CF-Connecting-IP': '192.0.2.1' } });
  assert.equal((await worker.fetch(r, { ADMISSION: { limit: async () => ({ success: false }) } })).status, 429);
});
