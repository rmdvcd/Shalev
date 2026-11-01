# Pruebas en dispositivo (USB)

Guía para probar Shalev en un teléfono real por USB: instalación, tests instrumentados, flujos manuales
guiados, checklist de cierre y solución de problemas. La verificación automática (tests JVM, lint, R8,
permisos, API mínima) vive en el CI; esto cubre lo que el CI no puede tocar: hardware, red local real,
cámara, biometría y dos teléfonos hablando entre sí.

> Auditoría 2026-10-14 (D-07): este documento se enlazaba desde `README.md` y `RELEASE.md` pero no
> existía. Se crea con lo que el proyecto ya usa; si un comando cambia, se cambia aquí.

## 1. Preparación

1. En el teléfono: Ajustes → Acerca del teléfono → tocar «Número de compilación» 7 veces.
2. Ajustes → Sistema → Opciones de desarrollador → activar **Depuración USB**.
3. Conectar por USB y aceptar la huella RSA en el teléfono.
4. Comprobar que el equipo ve el teléfono:

```bash
adb devices          # debe listar el equipo como «device», no «unauthorized»
adb shell getprop ro.build.version.sdk   # API del teléfono (Shalev pide 26+)
```

## 2. Instalar una build

```bash
# Debug (la que se usa a diario; convive con la de producción por el sufijo .debug)
./gradlew :app:installDebug

# Release sin firmar NO se instala encima de una firmada (y viceversa): si el teléfono ya tiene Shalev
# de producción, usa la debug o desinstala primero (se pierden los datos locales del debug, no los de prod).
adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk   # si existe build con splits
```

Desde 2026-10-14 el release genera un APK por ABI más el universal; para un teléfono moderno basta el
`arm64-v8a`.

## 3. Tests instrumentados

Son 145 tests (`src/androidTest` de `:app` y `:data`): Room con SQLCipher real, repositorios, sync por
socket y flujos con Compose. En CI corren semanalmente en emulador (trabajo `instrumentados`); aquí van
contra el teléfono conectado:

```bash
# Todos
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest

# Una clase o un test concreto
./gradlew :app:connectedDebugAndroidTest --tests "cu.shalev.app.SincronizacionRoomTest"
./gradlew :app:connectedDebugAndroidTest --tests "*.VentaRoomTest.registraYAnula"

# Informe legible
#   app/build/reports/androidTests/connected/index.html
#   data/build/reports/androidTests/connected/index.html
```

Antes de entregar: `connectedDebugAndroidTest` en verde Y `shalevTests` en verde. Si un instrumentado
falla solo en el teléfono y no en el emulador, sospecha primero de hora/zona horaria del equipo y de
permisos concedidos a mano en corridas anteriores (`adb shell pm clear cu.shalev.app.debug` lo deja limpio).

## 4. Pruebas manuales guiadas (por flujo)

Instala la debug y recorre, en este orden (cada flujo cubre lo que los tests no ven: permisos del sistema,
actividad real de Android y hardware):

1. **Arranque y acceso**: primer arranque, asistente de bienvenida, crear clave, salir y volver a entrar;
   bloquear con huella/cara si el equipo lo tiene y con la clave si no.
2. **Turno y venta en efectivo**: abrir turno con fondo, vender 2 productos, cerrar turno y comprobar el
   arqueo en Registros.
3. **Venta por transferencia**: QR de cobro, pegar el SMS de PAGOxMOVIL (y, si está activado, la captura
   automática desde la notificación), confirmar con nº de transacción.
4. **Venta por debajo del costo**: aplicar un preajuste de descuento que deje una línea al costo — la app
   debe ADVERTIR (comprobante/QR) y permitir registrar (auditoría §6.1); al crear el producto, en cambio,
   precio ≤ costo sigue bloqueado en el formulario.
5. **Inventario**: alta con foto (cámara y galería), código de barras con el escáner, ajustar existencias,
   crear un Elaborado con receta y venderlo (descuenta insumos).
6. **Respaldo**: exportar CON contraseña (viene marcado por defecto), importar en el mismo teléfono tras
   «Borrar todo»; repetir sin contraseña solo para comprobar el aviso. Un respaldo con contraseña
   incorrecta debe decir «Contraseña incorrecta», no «archivo dañado».
7. **Migración teléfono a teléfono**: con el segundo teléfono, leer el QR (ambos roles: este equipo envía
   y recibe), comprobar que los datos llegan y que el emisor ofrece borrarse al confirmar el receptor.
8. **Sincronización**: principal + secundaria en la misma wifi: vincular con código, vender en la
   secundaria sin conexión, reconectar y comprobar que el lote sube una sola vez; quitar la secundaria
   desde la principal y comprobar el aviso de «quitada».
9. **Actualización**: con un APK de versión mayor en el repo configurado, comprobar aviso de actualización,
   descarga, verificación e instalación; con el mismo `versionCode`, no debe ofrecer nada.
10. **Licencia**: pegar una licencia válida, una caducada y una de otra app; renovar; comprobar el estado
    en Ajustes sin conexión.

## 5. Checklist de cierre (antes de una release)

- [ ] `shalevTests`, `:app:lintDebug`, `:app:assembleRelease` y `shalevPermisos` en verde (o CI en verde).
- [ ] Instrumentados ejecutados (teléfono o trabajo `instrumentados` del CI) sin fallos nuevos.
- [ ] Flujo 1–3 (acceso, efectivo, transferencia) recorridos en el teléfono.
- [ ] Flujo 6 (respaldo con contraseña) recorrido: es la red de seguridad del negocio.
- [ ] Si tocó sync o migración: flujos 7 y 8 con dos teléfonos.
- [ ] Sin `Log.*` con datos del negocio en el código nuevo (R8 los elimina en release, pero el debug los
      mostraría en Logcat).
- [ ] Sin permisos nuevos en el manifiesto (la lista es cerrada: `AGENTS.md`).
- [ ] Versión: `versionCode`/`versionName` y notas en `Pendiente.md`/`docs/HISTORIAL_DESARROLLO.md` al día.
- [ ] Fila nueva en `docs/VERIFICACION.md` con fecha, commit y cifras de esta corrida.

## 6. Solución de problemas

| Síntoma | Causa probable | Qué hacer |
| --- | --- | --- |
| `adb devices` dice `unauthorized` | No se aceptó la huella en el teléfono | Desconectar, borrar autorizaciones en Opciones de desarrollador y reconectar |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Firma distinta (debug ↔ release) | Instalar la misma variante o desinstalar la otra (se pierden sus datos locales) |
| `INSTALL_FAILED_OLDER_SDK` | Teléfono con API < 26 | Shalev no se instala por debajo de Android 8; no es un fallo |
| Los instrumentados no encuentran el equipo | Emulador apagado y USB sin autorizar | `adb devices` debe listar 1 equipo antes de lanzar Gradle |
| La secundaria no ve a la principal | wifis distintas, aislamiento de clientes del router o VPN activa | Misma red, sin VPN; probar con la zona wifi del emisor |
| El QR de migración «no vale» | La clave del QR es de un solo uso y caduca con la sesión | Generar un QR nuevo desde el emisor y reintentar |
| Una captura Roborazzi cambió sola | Cambio visual real o fuente/escala del emulador | Revisar el diff en `app/capturas/`; si es el cambio buscado, versionar la nueva imagen (ver `CAPTURAS.md`) |


### Movimiento y accesibilidad

- Cambiar entre las secciones con la barra y deslizando en ambos sentidos; repetir rápidamente y volver con Atrás. La salida y la entrada deben seguir la dirección del gesto; al tocar la barra debe haber fundido.
- Hacer aparecer, cambiar y resolver alertas de inventario: comprobar que no haya saltos importantes y que la alerta visible abra su filtro.
- Cambiar períodos y datos de gráficos: los valores nuevos deben quedar legibles y no reiniciarse desde cero mientras el gráfico sigue visible.
- Abrir y cerrar ventanas con Atrás y tocando fuera, también con teclado y cambios sin guardar. Intentar toques repetidos durante la salida; verificar que confirmar o guardar no se duplique.
- Repetir con «Quitar animaciones», TalkBack, letra grande, claro/oscuro y navegación por gestos; comprobar fluidez en un dispositivo real.
