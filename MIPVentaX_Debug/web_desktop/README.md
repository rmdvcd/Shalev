# MIPVentaX Web-Desktop — Entorno Debug y Seed de Datos

Esta carpeta contiene la aplicación y datos de prueba para **MIPVentaX Web-Desktop** con el modelo gastronómico de 2 años de historial pre-cargado.

## Credenciales de acceso

- **URL:** [http://127.0.0.1:47812](http://127.0.0.1:47812)
- **Usuario administrador:** `admin`
- **Contraseña:** `Administrador123*`

## Cómo iniciar

### En Linux o macOS:
```bash
./iniciar_debug.sh
```

### En Windows:
Haz doble clic en `iniciar_debug.bat` o ejecuta en PowerShell:
```powershell
.\iniciar_debug.bat
```

## Estructura

- `DShalevData/dshalev.db`: Base de datos SQLite completamente sembrada con 730 días de turnos, arqueos, ventas, catálogo gastronómico, recetas, clientes y empleados.
- `sembrar_web_debug.py`: Script generador de la base de datos de escritorio.
