# Diseño de DShalev

## Propósito

DShalev es la edición de escritorio de Shalev: un programa Python portable que ejecuta un servidor web local y abre el panel en el navegador. El PC conserva la base del negocio; no se traslada la base a un servicio en la nube.

## Arquitectura

```text
DShalev.exe (Windows x64)
  ├─ FastAPI + Uvicorn                 servidor web/POS
  ├─ HTML + CSS adaptable              interfaz escritorio/tablet/móvil
  ├─ SQLite serializado en memoria     transacciones locales
  ├─ AES-256-GCM                       cifrado autenticado del archivo de datos
  └─ DPAPI de Windows                  protege la clave local para la cuenta/equipo

DShalevData/                           junto al EXE
  ├─ dshalev.sqlite3                   snapshot AES-GCM, nunca SQLite en claro
  ├─ database.key                      clave protegida por DPAPI en Windows
  ├─ portable-settings.json            modo de red y secreto de sesión
  └─ logs/DShalev.log                  errores de arranque, sin ventas ni credenciales
```

En ejecución, SQLite permanece en memoria compartida para todas las peticiones y, después de cada transacción escrita, se serializa, cifra con AES-GCM y se guarda mediante archivo temporal + reemplazo atómico. El nonce es aleatorio y la etiqueta autenticada liga el contenido a esta versión del formato. Un archivo sin clave, con clave incompatible o alterado no se reemplaza silenciosamente por una base vacía.

**Portabilidad de datos:** la clave local sigue ligada a Windows DPAPI; copiar `DShalevData` directamente a otro PC no es un método de migración. La sección Respaldo exporta/importa la base y los activos publicados (fotos y APK) en un contenedor cifrado con PBKDF2-HMAC-SHA256 + AES-GCM y contraseña (mínimo 12 caracteres); al restaurar, valida integridad y hashes, ejecuta migraciones y vuelve a proteger la clave con el mecanismo local. El flujo todavía requiere validar restauración cruzada en Windows. En desarrollo no-Windows, la clave local solo usa permisos del archivo.

## Modos de conexión

| Modo | Escucha | Uso previsto | Alcance |
|---|---|---|---|
| Local (predeterminado) | `127.0.0.1:47812` | Dueño frente al PC del negocio | Solo ese equipo |
| LAN | `0.0.0.0:47812` | Otra PC o navegador móvil en una misma Wi-Fi/LAN | Red local de confianza; HTTP aún no cifra el tráfico |
| Remoto | HTTPS en `0.0.0.0:47812` | PC fuera del local | Certificado TLS en DShalev y VPN/ruta/DNS/firewall configurados conscientemente |

DShalev no modifica el router, no activa UPnP, no instala un VPN, no crea túneles públicos y no abre el puerto HTTP a Internet. La recomendación para móvil por datos es una VPN. Si se usa un proxy o ruta pública, debe ser HTTPS con certificado vigente y seguir protegido por la cuenta local.

El navegador de otra PC/móvil puede abrir la misma interfaz web responsiva. Eso no equivale a sincronización nativa desde Shalev/IShalev.

## Integración de dispositivos secundarios

Android e IShalev usan SPVI v1 (QR de un uso, ECDH P-256, HKDF, AES-GCM, secuencias, lotes, instantáneas y comandos). DShalev implementa el listener TCP, pairing, persistencia de ventas/turnos/caja, stock/recetas, acciones para producto/insumo/servicio/preajuste, autorización de cierre y arqueo, propagación de anulación/corrección de ventas simples, y descarga por bloques de fotos y APK publicadas por el administrador. Los tests de loopback ejercitan estas rutas, pero **no** equivalen a Android/iPhone reales ni a dos redes físicas.

Límites aún abiertos: relay no integrado en DShalev (CGNAT puede impedir el acceso); corrección solo para ventas móviles de productos simples (no recetas, servicios ni insumos vendibles); activos multimedia/APK deben ser subidos manualmente; DShalev no valida la firma de APK; no se validó la compatibilidad real de fotos ni APK. La integración de licencia GL y sub-tokens comerciales firmados no está implementada. El período local de prueba no equivale a una licencia comercial.

### Fases de implementación

1. **Base local (implementada, Windows aún no verificado):** autenticación, turnos/caja, POS multicesta, catálogo/servicios, stock decimal de insumos, reportes y exportación CSV/XLSX de ventas, inventario y clientes (sin PDF).
2. **Datos de negocio (implementados parcialmente):** clientes, recetas con descuento de insumos, respaldo/restauración cifrados y migración de clave; reportes/exportación CSV y XLSX de ventas, inventario y clientes. Aún no hay exportación PDF ni paridad de todos los campos de clientes fijos móviles.
3. **SPVI (implementado y probado en loopback):** acciones de productos/insumos/servicios/recetas/preajustes, turnos/caja, aprobaciones de cierre/conteo, corrección/anulación limitada, fotos/APK publicadas manualmente. Pendiente Android/iOS físicos, compatibilidad real y revisión de firma APK.
4. **Acceso entre redes:** relay no integrado en DShalev. Su worker piloto existe en `relay/`; requiere URL desplegada y cableado del cliente Python, además de pruebas con dos móviles en redes reales.
5. **Licenciamiento y release:** integración de licencia GL/sub-token comercial no implementada; Windows EXE, migración cifrada cruzada y restauración sobre Windows deben verificarse fuera de este entorno.

## Modelo de seguridad

- Sin cuenta externa, telemetría ni analítica.
- Primer inicio exige crear usuario; contraseñas con PBKDF2-HMAC-SHA256 + salt, sesión firmada y cookies `HttpOnly`, `SameSite=Strict`, CSP, anti-iframe y validación CSRF.
- Cinco inicios fallidos por IP activan una espera de cinco minutos.
- AES-GCM protege el snapshot de SQLite en reposo; DPAPI protege la clave local en Windows. Windows user profile/BitLocker siguen siendo parte del límite de confianza.
- LAN por HTTP sirve solo para redes confiables: formulario y cookie viajan sin TLS. Usar HTTPS o VPN si hay riesgo de escucha. Remoto falla al arrancar si no existe el certificado o clave TLS configurados.
- No incluir secretos, certificados, base de datos o claves en Git/artefactos. El EXE de Actions se publica sin datos del negocio.

## Contratos de la primera entrega

- Dinero: entero en centavos CUP; el POS admite hasta 60 líneas por venta. Cantidades vendidas en productos/servicios son enteras; insumos almacenan hasta 3 decimales (milli-unidad).
- Solo se vende con turno abierto. La transacción de venta, la línea y el descuento de stock son atómicos, con verificación de stock dentro del bloqueo de escritura.
- Las plazas tienen credenciales SPVI y QR temporal; retirarlas revoca la clave y permite que el cliente borre datos tras un rechazo autenticado. Esto no reemplaza licencia GL ni prueba que apps móviles reales ya sean compatibles.

## Interfaz y accesibilidad (convenciones, 2026-11)

Convenciones adoptadas con el plan UI/UX/IX (`docs/PLAN_MEJORAS_UI_UX_IX.md`, ítems DS-1…DS-10). Son **obligatorias** en cualquier plantilla o estilo nuevo:

- **CSS legible (DS-9):** `static/app.css` está formateado (una línea por declaración). Antes se editaba minificado; para comprobar que el formateo no alteró el contenido se usa la comparación canónica de tokens con `tinycss2` (no una comparación textual, porque el original ya tenía saltos de línea parciales).
- **Foco siempre visible (DS-1):** regla global `:focus-visible` (outline de 2–3px con `outline-offset`) para TODO elemento interactivo, incluidos los que personalizan su foco. `scroll-padding-top` (90px escritorio / 78px móvil) evita que el foco quede oculto bajo la barra fija al avanzar con Tab.
- **Movimiento reducido (DS-1):** `prefers-reduced-motion: reduce` desactiva transiciones/animaciones del `theme.js` y del CSS; los cambios pasan a ser instantáneos (sustitución, no supresión de estados).
- **Skip-link y landmarks (DS-2):** todas las páginas con navegación tienen un enlace «Saltar al contenido» (`.skip-link`, visible con Tab) que apunta a `<main id="contenido">`; login/setup usan `<main>` como contenedor principal. El rail lateral lleva `<footer class="rail-footer">` real.
- **Doble envío (DS-3):** `static/forms.js` (cargado con `defer` en `base.html`) deshabilita el botón de envío al mandar cualquier `<form>` estándar y lo rehabilita si la petición falla antes de la navegación. Los formularios manejados **a mano** con fetch (ventas, respaldo, distribución) llevan `data-manual` para que forms.js no los toque (su propio JS ya gestiona el estado de envío).
- **Ayuda asociada al campo (DS-4):** cada texto de ayuda `<small>` lleva `id="ayuda-<campo>"` y el `<input>` correspondiente lleva `aria-describedby="ayuda-<campo>"` (o una lista de ids si también muestra error inline).
- **Errores inline por campo (DS-5, fase 1):** el servidor valida con helpers que lanzan `FieldError(field=…, mensaje)`; `flash_redirect(…, field=…)` lo pasa a la sesión y `context()` lo entrega a la plantilla como `flash_field`. La plantilla marca el campo con `aria-invalid="true"`, añade `aria-describedby` al `<small>` de error (`id="error-<campo>"` con clase `campo-error`) y el foco cae en el primer campo con error cuando el usuario vuelve al formulario. Fase 1 cubre `/inventario/nuevo`, `/clientes/nuevo` y `/equipo/nueva`; extender al resto de formularios siguiendo el mismo patrón (sin cambiar el CSS ni `forms.js`).
- **Regiones vivas en el POS (DS-6):** `#pos-lines` es `aria-live="polite"` y lleva `aria-busy="true"` mientras se valida/recuperan líneas (incluido el flujo de recuperación del carrito, con el `finally` que lo restablece). Las cargas asíncronas deben mantener esta señal: encender antes del fetch y apagar en `finally`.
- **Lint de plantillas (DS-7):** `ramplint --no-django dshalev/templates` corre en el CI de Windows (`.github/workflows/dshalev-windows.yml`, tras pytest). Cero hallazgos es el estado actual; cualquier hallazgo nuevo rompe la compilación. `--no-django` es el modo correcto para plantillas Jinja2 sueltas.
- **Página de ayuda (DS-8):** `/ayuda` (en el rail como «Ayuda») concentra atajos de teclado, un resumen del punto de venta y los pasos de respaldo/licencia con el teléfono de soporte (+5351815604). Debe mantenerse al día al añadir secciones nuevas.
- **Atajos de teclado:** F2 enfoca y selecciona el buscador del punto de venta (pos.js). Es el único atajo global; cualquier atajo nuevo debe documentarse en `/ayuda`.
- **Decisiones deliberadas de no-cambio:** sin HTMX/Tailwind/Alpine (stack actual: HTML + CSS + JS en vanilla); confirmaciones destructivas con diálogo de confirmación (no con «Deshacer» tipo snackbar), igual que en la app Android; conservar los comentarios con versión/decisión («el por qué») en código y CSS.
