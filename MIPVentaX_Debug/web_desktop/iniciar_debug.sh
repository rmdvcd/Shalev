#!/usr/bin/env bash
# Inicia MIPVentaX Web-Desktop con los datos de depuración pre-sembrados
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"

echo "Iniciando MIPVentaX Web-Desktop (Modo Debug)..."
echo "Datos: $DIR/DShalevData"
echo "Usuario administrador: admin"
echo "Contraseña: Administrador123*"
echo "Accede en tu navegador a: http://127.0.0.1:47812"

cd "$DIR"
export PYTHONPATH="$ROOT/Web-Desktop:$PYTHONPATH"
python3 -c "
import os
from pathlib import Path
import uvicorn
from dshalev.config import load_config
from dshalev.app import create_app

config = load_config(Path('$DIR'))
app = create_app(config)
uvicorn.run(app, host='0.0.0.0', port=47812, access_log=True)
"
