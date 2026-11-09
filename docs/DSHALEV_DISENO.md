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

1. **Base local (implementada, Windows aún no verificado):** autenticación, turnos/caja, POS multicesta, catálogo/servicios, stock decimal de insumos, reportes CSV y exportación de inventario/clientes.
2. **Datos de negocio (implementados parcialmente):** clientes, recetas con descuento de insumos, respaldo/restauración cifrados y migración de clave. Sin PDF/Excel y sin paridad de todos los campos de clientes fijos móviles.
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
