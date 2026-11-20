@echo off
REM Inicia MIPVentaX Web-Desktop con los datos de depuración pre-sembrados en Windows
echo Iniciando MIPVentaX Web-Desktop (Modo Debug)...
echo Datos: %~dp0DShalevData
echo Usuario administrador: admin
echo Contrasena: Administrador123*
echo Accede en tu navegador a: http://127.0.0.1:47812

cd /d "%~dp0"
set PYTHONPATH=%~dp0..\..\Web-Desktop;%PYTHONPATH%
python -c "import os; from pathlib import Path; import uvicorn; from dshalev.config import load_config; from dshalev.app import create_app; config = load_config(Path(r'%~dp0')); app = create_app(config); uvicorn.run(app, host='127.0.0.1', port=47812, access_log=True)"
pause
