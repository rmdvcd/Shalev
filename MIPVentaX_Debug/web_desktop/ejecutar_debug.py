#!/usr/bin/env python3
"""
Lanzador del servidor MIPVentaX Web-Desktop en modo debug.
Carga la configuración y la base de datos pre-sembrada en DShalevData.
"""

import os
import sqlite3
import sys
from pathlib import Path

DIR = Path(__file__).resolve().parent
REPO_ROOT = DIR.parents[1]
WEB_DESKTOP_DIR = REPO_ROOT / "Web-Desktop"

if str(WEB_DESKTOP_DIR) not in sys.path:
    sys.path.insert(0, str(WEB_DESKTOP_DIR))

try:
    import uvicorn
    from dshalev.config import load_config
    from dshalev.database import Database
    from dshalev.app import create_app
except ImportError as e:
    print(f"\n[ERROR] Faltan dependencias para ejecutar MIPVentaX Web-Desktop: {e}")
    print("Asegúrate de instalar los requisitos de Web-Desktop ejecutando:")
    print(f"  pip install -r \"{WEB_DESKTOP_DIR / 'requirements.txt'}\"\n")
    sys.exit(1)

def main():
    config = load_config(DIR)
    db_path = config.database_path
    seed_sql = DIR / "DShalevData" / "dshalev.db"

    # Si la base de datos cifrada no existe o está vacía pero existe dshalev.db,
    # inicializarla automáticamente con el seed de 2 años de historial gastronómico.
    if (not db_path.exists() or db_path.stat().st_size == 0) and seed_sql.exists():
        print("Inicializando base de datos local desde dshalev.db...")
        try:
            db = Database(db_path)
            with sqlite3.connect(seed_sql) as src:
                src.backup(db._anchor)
            db._persist()
            print("Base de datos gastronómica pre-sembrada cargada correctamente.")
        except Exception as exc:
            print(f"Aviso al importar base pre-sembrada: {exc}")

    app = create_app(config)

    print("\n=======================================================")
    print("  MIPVentaX Web-Desktop (Modo Debug)")
    print("=======================================================")
    print(f"  Directorio de datos: {DIR / 'DShalevData'}")
    print("  Usuario administrador: admin")
    print("  Contraseña: Administrador123*")
    print("  URL local: http://127.0.0.1:47812")
    print("=======================================================\n")

    uvicorn.run(app, host="127.0.0.1", port=47812, access_log=True)

if __name__ == "__main__":
    main()
