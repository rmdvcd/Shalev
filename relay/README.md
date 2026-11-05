# Intermediario remoto de Shalev — piloto

Worker + Durable Object por canal. Plan gratuito compatible con objetos SQLite;
no se guardan ventas, catálogos o claves en el servidor. Requiere Node 22.
No está desplegado ni habilitado por defecto en la app.

## Despliegue único por el responsable de Shalev

```bash
cd relay
npm ci
npm test
WRANGLER_SEND_METRICS=false npx wrangler login
WRANGLER_SEND_METRICS=false npm run deploy
```

El login se realiza directamente con Cloudflare; no compartir credenciales en chat.
Wrangler crea el namespace y publica una URL `https://shalev-relay.<subdominio>.workers.dev`.
Elige un nombre de Worker disponible en `wrangler.jsonc` si fuera necesario.
No hace falta comprar un dominio. Verifica en tu cuenta que el plan gratuito admite
estos recursos y revisa sus cuotas vigentes antes de publicar.

Compila **principal y secundarias** con la MISMA URL:

```bash
./gradlew :app:assembleDebug -PshalevRelayUrl=https://shalev-relay.<subdominio>.workers.dev
```

Los marcadores `<subdominio>` deben sustituirse por el dominio real obtenido al desplegar.
La URL pública no es una credencial; nunca pongas tokens en ella. Para builds de producción,
usa el proceso firmado habitual con la misma propiedad. Sin propiedad, no hay tráfico al relay
ni se pierde la compatibilidad local. No se permite cambiar la URL mediante QR.

## Funcionamiento y límites de esta primera implementación

- La secundaria prueba direcciones guardadas y descubrimiento LAN; después usa el relay.
- La principal abre canales salientes para hasta 16 empleados activos (sin puertos entrantes).
  Debe seguir abierta/disponible; el relay no es una copia en la nube de la base de datos.
- La vinculación inicial también puede ir por Internet con el QR habitual. Debe compartirse
  de forma segura y usarse antes de caducar. QR sin IPv4 requiere builds con relay habilitado.
- Cada sala se deriva por HMAC-SHA256 de negocio, empleado, finalidad y secreto: token de QR
  para vincular, clave de empleado para sesiones. No se envía ese secreto al relay.
- Se conservan ECDH P-256 + HMAC del QR y AES-GCM/HKDF del protocolo existente. Los saludos
  previos al canal cifrado contienen metadatos visibles al relay; no anunciar anonimato total.
- Dos conexiones máximo por sala; un rol no sustituye a otro. Conocer la capacidad de sala
  permite ocuparla (denegación de servicio), no descifrar ni autorizar operaciones del negocio.
- WebSocket Hibernation API; sin temporizadores persistentes ni escrituras SQL. Los attachments
  solo conservan rol, estado de emparejamiento y bytes transferidos.
- Fragmentos de hasta 64 KiB, 128 MiB enviados por extremo/sesión; para volúmenes mayores,
  usar LAN. Cola de entrada Android acotada a 16 MiB y contrapresión de salida.
- Admisión limitada a 60 conexiones/minuto por IP, antes de crear objetos. IP usada solo para
  el limitador, sin logs propios. NAT de operadores puede agrupar muchos teléfonos bajo una IP.
  No constituye protección completa ante ataques distribuidos o agotamiento de cuota.
- Reconexión principal con espera progresiva y variación aleatoria. Principal espera pareja
  hasta 120 s; secundaria hasta 25 s, además de los intentos LAN previos. No hay entrega si la
  principal no está disponible. Continúa el comportamiento offline existente, sin nueva nube.
- Sin logs propios ni observabilidad del Worker. Cloudflare sí procesa IP, horario, volumen,
  identificador opaco de sala y saludos. Revisar privacidad y condiciones del proveedor.
- Coste cero NO garantizado a cualquier escala; vigilar cuotas y no activar plan de pago
  automáticamente. Si se alcanza una cuota o falla Cloudflare, LAN sigue disponible.
- Probar acceso real desde los operadores de Cuba: el simulador no garantiza cobertura,
  acceso al proveedor, latencia ni consumo de datos móviles.

## Verificación

`npm test`: pruebas de rutas, roles, límites, cierre y una integración con el runtime
local workerd/Miniflare (upgrade real WebSocket y retransmisión binaria).
`WRANGLER_SEND_METRICS=false npx wrangler deploy --dry-run`: valida el empaquetado y bindings,
pero NO despliega ni demuestra funcionamiento en la cuenta o los móviles reales.

Los overrides de `undici` y `sharp` en package.json mantienen las dependencias de desarrollo
sin las vulnerabilidades detectadas en las versiones transitivas de Miniflare 4.

Antes de producción: compilar Android, ejecutar tests JVM/transportes y pruebas entre dos
móviles en redes distintas (vincular, sincronizar, fotos, APK, desconexión, revocación,
reconexión y consumo). Auditar seguridad y abuso del endpoint público.
