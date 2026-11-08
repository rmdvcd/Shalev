# DShalev — versión web de escritorio

DShalev es un **servidor web local en Python** pensado para ejecutarse en Windows como un único `DShalev.exe` portable. El navegador es la interfaz de escritorio: no necesita instalar un cliente separado. La base cifrada y la configuración se guardan en `DShalevData/` junto al ejecutable. En Windows, la clave local queda protegida por DPAPI y ligada a la cuenta/equipo; respaldar esa carpeta ayuda frente a pérdida del disco, pero copiarla a otro PC requiere que se implemente la migración cifrada.

> **Estado de esta entrega (0.1):** funciona el escritorio web, plazas de empleados, QR SPVI v1 de un solo uso y un listener TCP para emparejar y sincronizar lotes cifrados. Se guardan recibos de ventas de apps secundarias y se aplican movimientos de stock de productos. El contrato ya tiene pruebas automatizadas en loopback, pero **todavía no se ha validado en dispositivos reales Android ni iOS** y no es paridad completa: relay, licencia GL/sub-token, servicios/recetas/fotos, cierres y varios flujos aún faltan. El `.exe` de Windows tampoco está verificado en esta sesión.

## Ejecutar en desarrollo

Requiere Python 3.11 o 3.12. Desde esta carpeta:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python DShalev.py
```

En macOS/Linux se puede ejecutar `python3 DShalev.py`. En desarrollo se abre `http://127.0.0.1:47812`. En el primer inicio se crea una cuenta administradora; exige una contraseña de 12 caracteres o más. No existe contraseña predeterminada.

## EXE portable de Windows

En un PC Windows con Python instalado:

```powershell
.\build_windows.ps1
```

El ejecutable se crea en `dist\DShalev.exe`. El workflow `.github/workflows/dshalev-windows.yml` también prueba y publica el EXE como artefacto de Actions en Windows x64. Al ejecutar el EXE no se instala un servicio: se abre el navegador local. Los datos viven en `DShalevData/` al lado del programa. Cierra DShalev antes de respaldar la carpeta; para recuperar en el mismo PC conserva la misma cuenta Windows. La migración a otro equipo requiere la exportación cifrada planificada.

## Acceso de otros equipos

1. En **Inicio → Acceso**, selecciona **Red local** y guarda.
2. Reinicia DShalev. El servidor escucha en la interfaz de red del PC en el puerto `47812`.
3. En la otra PC o teléfono conectado a la misma red, abre una de las direcciones mostradas, por ejemplo `http://192.168.1.20:47812`, e inicia sesión.
4. Si no conecta, permite DShalev en el firewall de Windows para redes privadas. DShalev no cambia el router, no abre puertos con UPnP y no crea cuentas en la nube.

La web puede abrirse en el navegador móvil y se adapta a pantalla pequeña. Las apps Shalev/IShalev también pueden emparejarse desde **Equipo → Generar QR**, si DShalev está reiniciado en modo LAN o remoto. El QR SPVI v1 caduca en 10 minutos y solo se consume una vez; el listener nativo usa TCP desde el puerto 47811 y cifra las sesiones con ECDH P-256/HKDF/AES-GCM. Las ventas recibidas quedan en el historial de escritorio y sus movimientos de producto se aplican por UUID idempotente.

La conexión nativa actual es directa: no hay relay DShalev ni acceso por datos garantizado; en redes distintas se recomienda VPN. Una ruta directa exige que el host del QR resuelva y que el TCP al puerto codificado en el QR (47811–47820 por defecto) esté permitido extremo a extremo; DShalev no configura ni reenvía puertos, y NAT/CGNAT puede bloquearla. Para el navegador remoto se requiere HTTPS con certificado vigente. No reenvíes el puerto HTTP a Internet; la contraseña de sesión no cifra el resto del tráfico HTTP.

## Seguridad y límites

- El acceso empieza ligado a `127.0.0.1`. LAN y remoto se activan explícitamente desde la cuenta de administrador y se aplican al reiniciar.
- Contraseñas con PBKDF2-HMAC-SHA256 y salt aleatorio; cookie de sesión firmada, `HttpOnly`, `SameSite=Strict`, CSRF por formulario, límite temporal tras cinco fallos y cabeceras CSP/anti-iframe.
- La base SQLite se mantiene en memoria y se persiste como snapshot cifrado/autenticado AES-256-GCM. En Windows, su clave queda protegida por DPAPI de la cuenta/equipo; el modo de desarrollo no-Windows usa un archivo clave local y no tiene la protección DPAPI.
- En LAN todavía se usa HTTP: limitarlo a una red de confianza o usar VPN. El modo remoto solo arranca con rutas a un certificado y clave TLS existentes y la cookie va marcada `Secure`.
- No incluye relay, telemetría, cuenta de servicio, publicación automática de puertos ni integración con el worker remoto del relay. Los empleados nativos se pueden retirar; la app recibirá un rechazo autenticado al reconectarse.
- La sincronización SPVI persiste los lotes recibidos en la base cifrada y muestra ventas móviles; aplica los movimientos de producto una sola vez. Aún no replica toda la lógica Android/iOS: servicios, recetas, existencias decimales de insumos, aprobación de cierre, correcciones de venta, fotos y APK no están implementados.
- Los comandos soportados de productos/insumos usan claves persistidas por empleado y resultados cacheados en la misma transacción; las repeticiones devuelven el resultado previo. La caché se limita a las 512 claves recientes por empleado. Los comandos de servicios/recetas/preajustes aún no están implementados.
- La licencia que DShalev devuelve a las secundarias es una prueba local de siete días; no equivale a una licencia GL activa ni emite sub-tokens firmados. No usar para afirmar habilitación comercial.
- Los precios se guardan en centavos, el stock actual de la primera UI usa unidades enteras, cada venta requiere turno abierto y una transacción SQLite bloquea el registro para evitar vender stock inexistente concurrentemente.
- No se inventa una licencia GL: el plazo local de prueba es provisional; activación comercial y sub-token firmado requieren integrar el contrato GL en el escritorio.

## Estructura

- `dshalev/app.py`: rutas web, autenticación, administración de plazas y QR SPVI.
- `dshalev/sync_protocol.py`, `dshalev/sync_server.py`: encuadre, vinculación ECDH, canal cifrado, lotes/recibos y listener TCP.
- `dshalev/database.py`: SQLite, snapshots cifrados, migraciones y persistencia.
- `dshalev/security.py`: hash de contraseña y validación CSRF.
- `dshalev/config.py`, `dshalev/run.py`: ubicación portable y modos de red/arranque.
- `dshalev/templates/`, `dshalev/static/`: interfaz HTML accesible y adaptable.
- `tests/`: pruebas de configuración/autenticación/ventas y de encuadre, QR, ECDH, AES-GCM, pairing, sincronización TCP, movimientos idempotentes y revocación (loopback; no sustituyen pruebas con Android/iPhone físicos).
