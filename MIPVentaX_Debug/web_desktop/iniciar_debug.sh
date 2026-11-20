#!/usr/bin/env bash
# Inicia MIPVentaX Web-Desktop con los datos de depuración pre-sembrados
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Iniciando MIPVentaX Web-Desktop (Modo Debug)..."
echo "Datos: $DIR/DShalevData"
echo "Usuario administrador: admin"
echo "Contraseña: Administrador123*"
echo "Accede en tu navegador a: http://127.0.0.1:47812"

cd "$DIR"
python3 ejecutar_debug.py
