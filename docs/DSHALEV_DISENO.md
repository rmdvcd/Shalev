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

**Límite de portabilidad de datos:** el `.exe` es portable; la clave de la base se vincula a Windows DPAPI y no se debe prometer que `DShalevData` se abra al copiarlo a otro PC o a otro usuario. La migración segura entre equipos requiere un formato de respaldo con contraseña, previsto para una etapa posterior. En desarrollo no-Windows, la clave local se guarda con permisos restringidos, pero no se beneficia de DPAPI.

## Modos de conexión

| Modo | Escucha | Uso previsto | Alcance |
|---|---|---|---|
| Local (predeterminado) | `127.0.0.1:47812` | Dueño frente al PC del negocio | Solo ese equipo |
| LAN | `0.0.0.0:47812` | Otra PC o navegador móvil en una misma Wi-Fi/LAN | Red local de confianza; HTTP aún no cifra el tráfico |
| Remoto | HTTPS en `0.0.0.0:47812` | PC fuera del local | Certificado TLS en DShalev y VPN/ruta/DNS/firewall configurados conscientemente |

DShalev no modifica el router, no activa UPnP, no instala un VPN, no crea túneles públicos y no abre el puerto HTTP a Internet. La recomendación para móvil por datos es una VPN. Si se usa un proxy o ruta pública, debe ser HTTPS con certificado vigente y seguir protegido por la cuenta local.

El navegador de otra PC/móvil puede abrir la misma interfaz web responsiva. Eso no equivale a sincronización nativa desde Shalev/IShalev.

## Integración de dispositivos secundarios

Android e IShalev usan SPVI v1 (saludos, QR de un solo uso, ECDH P-256, HKDF, AES-GCM por sentido, secuencias, lotes, instantáneas y comandos). DShalev ya tiene una implementación Python de QR `SPVI-VINC1:`, listener TCP, pairing/clave por empleado, canal cifrado, snapshot, recibos de ventas, movimientos de stock de producto y rechazo autenticado al retirar una plaza. Sus pruebas automatizadas cubren el intercambio en loopback; **no** se ha validado todavía contra un Android ni un iPhone reales. No se debe presentar como paridad nativa completa.

Persisten límites importantes: no está conectado el relay, de modo que CGNAT puede bloquear el acceso directo; el listener ofrece un conjunto limitado de acciones (con deduplicación persistente de comandos soportados), recibos de ventas/caja/turnos no implementan todos los servicios de negocio, no hay aprobación/corrección/fotos/APK, y las licencias GL/sub-tokens firmados todavía no se emiten. El plazo de prueba local no equivale a una licencia válida comercial. La etapa de validación de compatibilidad con dispositivos y la integración restante sigue pendiente.

### Fases de implementación

1. **Base segura y verificable (0.1, en este branch):** script y workflow preparados para generar el EXE Windows (aún sin build Windows verificado); primer inicio seguro; usuarios; turnos; venta unitaria; stock y panel; plazas y permisos web; archivo cifrado en Windows; acceso local/LAN/HTTPS explícito.
2. **Paridad funcional:** servicios/insumos con unidades decimales, recetas, clientes fijos, arqueo, corrección/anulación, registros, análisis, exportación PDF/Excel/CSV, respaldo e importación/exportación Android/iOS.
3. **Cliente nativo (parcial):** QR/listener y sincronización cifrada implementados con pruebas loopback. Pendiente: pruebas Android↔DShalev e IShalev↔DShalev en LAN; GL/sub-token reales; ampliar acciones a servicios/recetas/preajustes, movimientos decimales de insumos, turnos/caja completos, correcciones, fotos y APK.
4. **Acceso entre redes:** integrar la conexión principal del relay autorizado en Python y probar desde Shalev/IShalev sobre datos móviles; documentar límites del operador, privacidad, cuotas y respuesta cuando el host no esté disponible. El host directo configurado no sustituye al relay.
5. **Estabilización:** instalador/actualizador verificable opcional, respaldo cifrado con contraseña para cambio de PC, recuperación de clave, migraciones y pruebas de restauración.

## Modelo de seguridad

- Sin cuenta externa, telemetría ni analítica.
- Primer inicio exige crear usuario; contraseñas con PBKDF2-HMAC-SHA256 + salt, sesión firmada y cookies `HttpOnly`, `SameSite=Strict`, CSP, anti-iframe y validación CSRF.
- Cinco inicios fallidos por IP activan una espera de cinco minutos.
- AES-GCM protege el snapshot de SQLite en reposo; DPAPI protege la clave local en Windows. Windows user profile/BitLocker siguen siendo parte del límite de confianza.
- LAN por HTTP sirve solo para redes confiables: formulario y cookie viajan sin TLS. Usar HTTPS o VPN si hay riesgo de escucha. Remoto falla al arrancar si no existe el certificado o clave TLS configurados.
- No incluir secretos, certificados, base de datos o claves en Git/artefactos. El EXE de Actions se publica sin datos del negocio.

## Contratos de la primera entrega

- Dinero: entero en centavos CUP; una venta de v0.1 contiene un artículo/una línea.
- Stock de productos: unidades enteras; precisión decimal de insumos llegará con paridad.
- Solo se vende con turno abierto. La transacción de venta, la línea y el descuento de stock son atómicos, con verificación de stock dentro del bloqueo de escritura.
- Las plazas tienen credenciales SPVI y QR temporal; retirarlas revoca la clave y permite que el cliente borre datos tras un rechazo autenticado. Esto no reemplaza licencia GL ni prueba que apps móviles reales ya sean compatibles.
