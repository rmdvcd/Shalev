// Intermediario opaco: no interpreta saludos, claves, ventas ni catálogos.
export const MAX_CHUNK = 64 * 1024;
export const MAX_SESSION = 128 * 1024 * 1024;
export function route(request) {
  const url = new URL(request.url);
  if (request.method !== 'GET' || request.headers.get('Upgrade')?.toLowerCase() !== 'websocket') return null;
  // No se ofrece acceso desde navegadores; los clientes Android no envían Origin.
  if (request.headers.has('Origin') || url.search) return null;
  return /^\/v1\/([a-f0-9]{64})\/(principal|secundaria)$/.exec(url.pathname);
}
