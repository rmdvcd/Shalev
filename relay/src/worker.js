import { route, MAX_CHUNK, MAX_SESSION } from './protocol.js';

export default {
  async fetch(request, env) {
    const r = route(request);
    if (!r) return new Response('Solicitud no válida', { status: 400 });
    // Límite por IP antes de crear un objeto; no registra direcciones ni cuerpos.
    const ip = request.headers.get('CF-Connecting-IP');
    if (!ip) return new Response('Solicitud no válida', { status: 400 });
    if (!(await env.ADMISSION.limit({ key: ip })).success)
      return new Response('Demasiados intentos', { status: 429 });
    return env.ROOMS.get(env.ROOMS.idFromName(r[1])).fetch(request);
  },
};

// Una sala de capacidad impredecible, derivada de un secreto por empleado y finalidad.
// No escribe datos de negocio en SQLite. Attachments: rol, emparejado y contador de bytes.
export class RelayRoom {
  constructor(ctx) { this.ctx = ctx; }
  async fetch(request) {
    const r = route(request);
    if (!r) return new Response('Solicitud no válida', { status: 400 });
    const role = r[2];
    const peers = this.ctx.getWebSockets();
    if (peers.some(s => s.deserializeAttachment().role === role) || peers.length >= 2)
      return new Response('Canal ocupado', { status: 409 });
    const pair = new WebSocketPair();
    const client = pair[0], server = pair[1];
    server.serializeAttachment({ role, paired: false, bytes: 0 });
    this.ctx.acceptWebSocket(server);
    if (peers.length === 1) {
      for (const s of [peers[0], server]) {
        s.serializeAttachment({ ...s.deserializeAttachment(), paired: true });
        s.send('ready');
      }
    }
    return new Response(null, { status: 101, webSocket: client });
  }
  webSocketMessage(socket, message) {
    const meta = socket.deserializeAttachment();
    if (!(message instanceof ArrayBuffer) || message.byteLength === 0 || message.byteLength > MAX_CHUNK || !meta.paired) {
      this.finish(socket, 1008); return;
    }
    meta.bytes += message.byteLength;
    if (meta.bytes > MAX_SESSION) { this.finish(socket, 1009); return; }
    socket.serializeAttachment(meta);
    const other = this.ctx.getWebSockets().find(s => s !== socket);
    if (!other) { this.finish(socket, 1001); return; }
    try { other.send(message); } catch { this.finish(socket, 1011); }
  }
  webSocketClose(socket) { this.finish(socket, 1000); }
  webSocketError(socket) { this.finish(socket, 1011); }
  finish(socket, code) {
    // No deja a una sesión nueva heredar bytes de una conexión anterior.
    for (const s of this.ctx.getWebSockets()) { try { s.close(code, 'Canal cerrado'); } catch {} }
    try { socket.close(code, 'Canal cerrado'); } catch {}
  }
}
