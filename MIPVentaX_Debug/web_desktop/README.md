# MIPVentaX Web-Desktop — Entorno Debug y Seed de Datos

Esta carpeta contiene la aplicación y datos de prueba para **MIPVentaX Web-Desktop** con el modelo gastronómico de 2 años de historial pre-cargado.

## Credenciales de acceso

- **URL:** [http://127.0.0.1:47812](http://127.0.0.1:47812)
- **Usuario administrador:** `admin`
- **Contraseña:** `Administrador123*`

## Cómo iniciar

### En Windows:
Haz doble clic en `iniciar_debug.bat` o ejecuta en cmd/PowerShell:
```bat
iniciar_debug.bat
```

### En Linux o macOS:
```bash
./iniciar_debug.sh
```

*(O directamente con Python en cualquier sistema operativo: `python ejecutar_debug.py`)*

## Estructura

- `ejecutar_debug.py`: Lanzador multiplataforma de alta compatibilidad que inicializa la base pre-sembrada y levanta el servidor local.
- `iniciar_debug.bat`: Script de un clic para Windows.
- `iniciar_debug.sh`: Script de un clic para Linux / macOS.
- `DShalevData/dshalev.db`: Base de datos SQLite pre-sembrada con 730 días de turnos, arqueos, ventas, catálogo gastronómico, recetas, clientes y empleados.
- `sembrar_web_debug.py`: Script generador de la base de datos de escritorio.

## Qué incluye el seed de escritorio (1.0.9)

Además del histórico, el volcado deja el negocio como lo dejaría un uso real, para que la app de debug no arranque
a medias:

- **Módulos** activos (`VENTAS`, `INVENTARIO`, `SERVICIOS`) en `app_meta.modulos`, que es lo que filtra el rail.
- **Recorridos guiados** de las seis secciones marcados como vistos (`<clave>_tour_shown`), para que no salten al
  abrir; se repiten desde `/ayuda` o `/recorrido/<clave>` cuando se quieren revisar.
- **Dos preajustes de precio** con condición (`volume_minimum` y `customer_type`), uno de ellos sobre los cócteles.
- **`schema_version` en 11**, de modo que abrir la app no tenga que migrar nada delante de quien mira la demo.

`ejecutar_debug.py` volca este `dshalev.db` a la base cifrada la primera vez, y `create_app` la migra a la versión
de esquema actual si el volcado es anterior.
