# Propuesta: canje comercial GL para DShalev (no implementada)

> **2026-11-10:** la base local (identidad P-256, configuración fijada y almacén protegido) ya está implementada; el
> canje y el estado siguen bloqueados por el contrato GL. Ver [`DSHALEV_LICENCIA_GL.md`](DSHALEV_LICENCIA_GL.md).

**Estado:** diseño inicial, no es un contrato vigente ni habilita ventas comerciales. El usuario ha indicado que GL deberá canjear un código de 16 caracteres mediante un servicio aún por construir. El contrato existente de Android activa con `SPVI2:` (ver `LICENSE_CLIENT.md`); el código 4×4 por sí solo no sustituye su firma ni su vínculo a dispositivo. No introducir claves privadas de GL en DShalev.

## Principios propuestos

- Código de 16 símbolos alfanuméricos ASCII agrupados `XXXX-XXXX-XXXX-XXXX`, generado mediante aleatoriedad criptográfica, nunca secuencial. Canonicalizar sin distinción de mayúsculas ignorando espacios y guiones; rechazar cualquier otro carácter o longitud. La validez sintáctica no implica activación.
- Servicio GL accesible mediante HTTPS con verificación de certificado y nombre de servidor; configuraciones de producción fijadas y aprobadas antes de publicar. Nunca enviar el código a un servidor elegido arbitrariamente por una entrada web. No registrar códigos, tokens o datos personales, incluidos logs de proxy.
- Un canje aceptado debe producir una **licencia firmada** por GL con identificador de licencia, producto/destinatario DShalev, identidad o clave pública del dispositivo, emisión, vencimiento o carácter perpetuo y derechos explícitos. El cliente debe validar firma contra claves públicas de confianza versionadas, destinatario, dispositivo, límites y fechas antes de guardar o habilitar nada, y repetir validación en cada uso. La sintaxis definitiva, algoritmos, claves, revocación, versión y vectores se acordarán con GL; no llamar `SPVI2` a un formato diferente.
- Un dispositivo necesita identidad criptográfica persistente protegida por el sistema operativo. Definir recuperación/migración antes de producción: las restauraciones no deben habilitar licencias en un equipo distinto ni destruir una licencia vigente por un respaldo antiguo. Protección DPAPI no equivale a hardware antirrobo.
- El servidor almacena códigos con un verificador protegido por secreto del servidor, no el código en claro; transacción atómica de canje vincula código y dispositivo. Reintentos idempotentes para la misma operación, evitando gastar el código si se perdió la respuesta. Rechazar canjes de otro dispositivo salvo procedimiento explícito de transferencia; límites y mensajes uniformes contra enumeración, rate limiting y auditoría sin secretos.
- Un servicio no disponible o una respuesta inválida no activa nada ni borra una licencia previa válida. Definir política explícita y aprobada de funcionamiento sin conexión, tiempo, renovación, revocación y comportamiento de fallo; no inventar una tolerancia indefinida para licencias revocadas. Aclarar concesión de secundarias y sub-tokens firmados, que DShalev aún no emite.

## Trabajo y decisiones pendientes antes de implementar

1. Elegir dónde se alojará el servicio, su operador y sus credenciales administrativas, recuperación y modelo de amenazas. Determinar cómo el emisor GL actual generará/cargará y revocará códigos.
2. Diseñar API versionada y canónica, material firmado, claves de prueba/producción y rotación; decidir vínculo por instalación o por local independiente, transferencia y número de activaciones permitido.
3. Implementar servidor con almacenamiento transaccional, protección ante abuso y pruebas concurrentes/idempotentes; desplegar y configurar HTTPS. No publicar credenciales en el repositorio.
4. Implementar cliente y pruebas con claves de prueba: firma o contenido mutados, dispositivo distinto, vencimiento, revocación, reloj, reinicio, fallo de red, doble canje y respuesta perdida. Probar extremo a extremo contra GL real y en Windows antes de declarar activación comercial.

Nada de esto cambia el protocolo Android SPVI existente ni activa el panel de licencia actual de DShalev.
